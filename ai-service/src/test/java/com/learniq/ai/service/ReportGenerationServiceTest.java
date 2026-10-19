package com.learniq.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.common.dto.ExamPerformanceDto;
import com.learniq.common.dto.StudentTrendReportDto;
import com.learniq.common.dto.SubjectNetScoreDto;
import com.learniq.common.event.AiReportRequestedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportGenerationServiceTest {

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private S3Client s3Client;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ReportGenerationService reportGenerationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reportGenerationService, "bucketName", "test-bucket");
    }

    @Test
    @DisplayName("generateAndSaveExamReport - Generates report with LLM, uploads to S3, and publishes ai-report-completed")
    void generateAndSaveExamReport_success() {
        String reportId = UUID.randomUUID().toString();
        String studentId = UUID.randomUUID().toString();
        String tenantId = "test_tenant";
        String payloadData = "Total:95.00|Percentile:8.00|Matematik:C35W5N33.75";

        Map<String, Object> event = Map.of(
                "reportId", reportId,
                "studentId", studentId,
                "tenantId", tenantId,
                "payloadData", payloadData
        );

        String mockAiResponse = "# Deneme Sınavı Değerlendirmesi\nÖğrenci genel olarak üstün başarı sergilemiştir.";

        when(chatClient.prompt().user(anyString()).call().content()).thenReturn(mockAiResponse);

        reportGenerationService.generateAndSaveExamReport(event);

        // S3'e yüklendi mi?
        ArgumentCaptor<PutObjectRequest> s3Captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(s3Captor.capture(), any(RequestBody.class));
        PutObjectRequest putReq = s3Captor.getValue();
        assertEquals("test-bucket", putReq.bucket());
        assertNotNull(putReq.key());

        // Kafka'ya ai-report-completed eventi fırlatıldı mı?
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("ai-report-completed"), eq(reportId), payloadCaptor.capture());
        String publishedJson = payloadCaptor.getValue();

        assertNotNull(publishedJson);
        org.junit.jupiter.api.Assertions.assertTrue(publishedJson.contains(reportId));
        org.junit.jupiter.api.Assertions.assertTrue(publishedJson.contains(putReq.key()));
    }

    @Test
    @DisplayName("generateAndSaveReport (Yol B) - trend raporu S3'e yüklenir, tamamlanma eventi yayınlanmaz")
    void generateAndSaveReport_trend_uploadsButDoesNotPublish() {
        UUID studentId = UUID.randomUUID();
        StudentTrendReportDto trend = StudentTrendReportDto.builder()
                .studentId(studentId)
                .totalExamsTaken(1)
                .examHistory(List.of(ExamPerformanceDto.builder()
                        .examTitle("Deneme 1")
                        .examType("TYT")
                        .examDate(LocalDate.of(2026, 2, 1))
                        .totalScore(new BigDecimal("90.00"))
                        .classRank(1)
                        .schoolRank(3)
                        .subjectScores(List.of(SubjectNetScoreDto.builder()
                                .subjectName("Matematik")
                                .netScore(new BigDecimal("30.00"))
                                .build()))
                        .build()))
                .build();

        AiReportRequestedEvent event = AiReportRequestedEvent.builder()
                .tenantId("t1")
                .studentId(studentId)
                .trendData(trend)
                .build();

        when(chatClient.prompt().user(anyString()).call().content()).thenReturn("# Başlık\nÖzet satırı");

        reportGenerationService.generateAndSaveReport(event);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertTrue(captor.getValue().key().startsWith("reports/t1/student_" + studentId + "_"));
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("generateAndSaveExamReport - tenantId yoksa S3 anahtarı 'public' kullanır")
    void generateAndSaveExamReport_missingTenant_defaultsToPublic() {
        Map<String, Object> event = new HashMap<>();
        event.put("reportId", "r1");
        event.put("studentId", "s1");
        event.put("payloadData", "Total:1.0");

        when(chatClient.prompt().user(anyString()).call().content()).thenReturn("rapor");

        reportGenerationService.generateAndSaveExamReport(event);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertTrue(captor.getValue().key().startsWith("reports/public/"),
                "tenantId verilmediğinde 'public' varsayılmalı, gelen: " + captor.getValue().key());
    }

    @Test
    @DisplayName("generateAndSaveExamReport - summary başlık/madde işaretleri atlanarak ilk düz satırdan alınır")
    void generateAndSaveExamReport_extractsSummaryFromBody() {
        Map<String, Object> event = Map.of(
                "reportId", "r1",
                "studentId", "s1",
                "tenantId", "t1",
                "payloadData", "Total:1.0");

        when(chatClient.prompt().user(anyString()).call().content())
                .thenReturn("# Başlık\n* madde işareti\nGerçek özet satırı.");

        reportGenerationService.generateAndSaveExamReport(event);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("ai-report-completed"), eq("r1"), payloadCaptor.capture());
        assertTrue(payloadCaptor.getValue().contains("Gerçek özet satırı."),
                "summary düz metin satırından çıkarılmalı: " + payloadCaptor.getValue());
    }
}
