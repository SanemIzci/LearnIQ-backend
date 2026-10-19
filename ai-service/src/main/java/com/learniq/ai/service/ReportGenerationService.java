package com.learniq.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.common.dto.ExamPerformanceDto;
import com.learniq.common.dto.StudentTrendReportDto;
import com.learniq.common.event.AiReportRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationService {

    private final ChatClient chatClient;
    private final S3Client s3Client;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    /**
     * Legacy/Trend AI Report generation (Yol B: Analytics flow)
     */
    public void generateAndSaveReport(AiReportRequestedEvent event) {
        log.info("Starting AI trend report generation for student: {} in tenant: {}", event.getStudentId(), event.getTenantId());
        
        String prompt = buildPrompt(event.getTrendData());

        log.debug("Prompt built: \n{}", prompt);

        String aiResponse = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        log.info("AI report generated successfully. Length: {}", aiResponse.length());

        uploadToS3(event.getTenantId(), event.getStudentId().toString(), aiResponse);
    }

    /**
     * On-demand Exam AI Report generation (Yol A: Student Module flow)
     */
    public void generateAndSaveExamReport(Map<String, Object> event) {
        String reportId = (String) event.get("reportId");
        String studentId = (String) event.get("studentId");
        String tenantId = (String) event.getOrDefault("tenantId", "public");
        String payloadData = (String) event.get("payloadData");

        log.info("Starting on-demand exam AI report generation. reportId: {}, studentId: {}, tenantId: {}",
                reportId, studentId, tenantId);

        String prompt = buildExamPrompt(studentId, payloadData);

        String aiResponse = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        log.info("AI exam report generated successfully for reportId: {}. Length: {}", reportId, aiResponse.length());

        String s3Key = uploadToS3(tenantId, studentId, aiResponse);
        String summary = extractSummary(aiResponse);

        publishCompletedEvent(reportId, s3Key, summary, studentId, tenantId);
    }

    private void publishCompletedEvent(String reportId, String s3Key, String summary, String studentId, String tenantId) {
        Map<String, Object> completedEvent = new HashMap<>();
        completedEvent.put("eventType", "AI_REPORT_COMPLETED");
        completedEvent.put("reportId", reportId);
        completedEvent.put("s3Key", s3Key);
        completedEvent.put("summary", summary);
        completedEvent.put("studentId", studentId);
        completedEvent.put("tenantId", tenantId);

        try {
            String jsonPayload = objectMapper.writeValueAsString(completedEvent);
            kafkaTemplate.send("ai-report-completed", reportId, jsonPayload);
            log.info("Published ai-report-completed event for reportId: {}, s3Key: {}", reportId, s3Key);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ai-report-completed event for reportId: {}", reportId, e);
        }
    }

    private String buildExamPrompt(String studentId, String payloadData) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert academic advisor and test coach. ");
        sb.append("Analyze the following student's single exam result and performance breakdown:\n\n");
        sb.append("Student ID: ").append(studentId).append("\n");
        sb.append("Performance Data: ").append(payloadData).append("\n\n");
        sb.append("Please provide an encouraging, actionable, and analytical feedback report in Markdown:\n");
        sb.append("1. **Executive Summary**: 1-2 sentences summarizing performance and standing.\n");
        sb.append("2. **Subject Breakdown**: Strengths, weaknesses, and key improvement areas.\n");
        sb.append("3. **Actionable Study Plan**: Concrete study advice for upcoming exams.\n");
        return sb.toString();
    }

    private String extractSummary(String content) {
        if (content == null || content.isBlank()) {
            return "Rapor hazırlandı.";
        }
        return content.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank() && !line.startsWith("#") && !line.startsWith("*"))
                .findFirst()
                .map(line -> line.length() > 250 ? line.substring(0, 247) + "..." : line)
                .orElse("Deneme sınavı başarıyla analiz edildi.");
    }

    private String buildPrompt(StudentTrendReportDto trendData) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert academic advisor for a Test Prep Academy. ");
        sb.append("Analyze the following student's exam history and provide a personalized, encouraging, yet analytical report.\n");
        sb.append("Identify their strengths, weaknesses, and a suggested study plan.\n\n");
        sb.append("Format the report in Markdown.\n\n");

        sb.append("Student ID: ").append(trendData.getStudentId()).append("\n");
        sb.append("Total Exams Taken: ").append(trendData.getTotalExamsTaken()).append("\n\n");

        sb.append("Exam History:\n");
        for (ExamPerformanceDto exam : trendData.getExamHistory()) {
            sb.append(String.format("- Exam: %s (%s) on %s\n", exam.getExamTitle(), exam.getExamType(), exam.getExamDate()));
            sb.append(String.format("  Score: %s, Class Rank: %d, School Rank: %d\n", exam.getTotalScore(), exam.getClassRank(), exam.getSchoolRank()));
            
            String subjects = exam.getSubjectScores().stream()
                    .map(s -> String.format("%s (Net: %s)", s.getSubjectName(), s.getNetScore()))
                    .collect(Collectors.joining(", "));
            sb.append("  Subjects: ").append(subjects).append("\n");
        }

        return sb.toString();
    }

    private String uploadToS3(String tenantId, String studentId, String content) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String objectKey = String.format("reports/%s/student_%s_%s.md", tenantId, studentId, timestamp);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType("text/markdown")
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(content.getBytes(StandardCharsets.UTF_8)));
        log.info("Report uploaded to S3: s3://{}/{}", bucketName, objectKey);
        return objectKey;
    }
}
