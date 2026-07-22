package com.learniq.ai.service;

import com.learniq.common.dto.ExamPerformanceDto;
import com.learniq.common.dto.StudentTrendReportDto;
import com.learniq.common.event.AiReportRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationService {

    private final ChatClient chatClient;
    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    public void generateAndSaveReport(AiReportRequestedEvent event) {
        log.info("Starting AI report generation for student: {} in tenant: {}", event.getStudentId(), event.getTenantId());
        
        String prompt = buildPrompt(event.getTrendData());

        log.debug("Prompt built: \n{}", prompt);

        // Call the LLM
        String aiResponse = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        log.info("AI report generated successfully. Length: {}", aiResponse.length());

        // Upload to S3
        uploadToS3(event.getTenantId(), event.getStudentId().toString(), aiResponse);
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

    private void uploadToS3(String tenantId, String studentId, String content) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String objectKey = String.format("reports/%s/student_%s_%s.md", tenantId, studentId, timestamp);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType("text/markdown")
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(content.getBytes(StandardCharsets.UTF_8)));
        log.info("Report uploaded to S3: s3://{}/{}", bucketName, objectKey);
    }
}
