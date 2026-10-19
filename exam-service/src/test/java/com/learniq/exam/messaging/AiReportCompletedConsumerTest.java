package com.learniq.exam.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.exam.domain.AiReport;
import com.learniq.exam.repository.AiReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiReportCompletedConsumerTest {

    @Mock
    private AiReportRepository aiReportRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AiReportCompletedConsumer consumer;

    @Test
    @DisplayName("consume - Kafka event geldiğinde DB güncellenir")
    void consume_validMessage_updatesDatabase() {
        UUID reportId = UUID.randomUUID();
        String s3Key = "reports/final.pdf";
        String summary = "Sen çok zekisin";

        String jsonMessage = String.format(
            "{\"reportId\":\"%s\",\"s3Key\":\"%s\",\"summary\":\"%s\"}",
            reportId, s3Key, summary
        );

        AiReport existingReport = new AiReport();
        existingReport.setId(reportId);
        existingReport.setStatus(AiReport.ReportStatus.PENDING);

        when(aiReportRepository.findById(reportId)).thenReturn(Optional.of(existingReport));

        consumer.consume(jsonMessage);

        assertEquals(AiReport.ReportStatus.READY, existingReport.getStatus());
        assertEquals(s3Key, existingReport.getS3Key());
        assertEquals(summary, existingReport.getSummary());

        verify(aiReportRepository).save(existingReport);
    }

    @Test
    @DisplayName("consume - Geçersiz JSON gelirse hata fırlatmaz, loglar (graceful degrade)")
    void consume_invalidJson_handlesGracefully() {
        String jsonMessage = "{invalid-json}";

        consumer.consume(jsonMessage);

        verify(aiReportRepository, never()).findById(any(UUID.class));
        verify(aiReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("consume - Veritabanında rapor bulunamazsa hata fırlatmaz, loglar")
    void consume_reportNotFound_handlesGracefully() {
        UUID reportId = UUID.randomUUID();
        String jsonMessage = String.format(
            "{\"reportId\":\"%s\",\"s3Key\":\"test\"}", reportId
        );

        when(aiReportRepository.findById(reportId)).thenReturn(Optional.empty());

        consumer.consume(jsonMessage);

        verify(aiReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("consume - Eksik s3Key içeren mesaj atlanır (DB'ye hiç gidilmez)")
    void consume_missingS3Key_skips() {
        UUID reportId = UUID.randomUUID();
        String jsonMessage = String.format("{\"reportId\":\"%s\",\"summary\":\"x\"}", reportId);

        consumer.consume(jsonMessage);

        verify(aiReportRepository, never()).findById(any(UUID.class));
        verify(aiReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("consume - Geçersiz (UUID olmayan) reportId hata fırlatmaz, kaydetmez")
    void consume_invalidReportId_handlesGracefully() {
        String jsonMessage = "{\"reportId\":\"not-a-uuid\",\"s3Key\":\"reports/x.md\"}";

        consumer.consume(jsonMessage);

        verify(aiReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("consume - Aynı event iki kez gelirse (at-least-once) rapor READY kalır")
    void consume_duplicateDelivery_isIdempotent() {
        UUID reportId = UUID.randomUUID();
        String jsonMessage = String.format(
                "{\"reportId\":\"%s\",\"s3Key\":\"reports/x.md\",\"summary\":\"ok\"}", reportId);

        AiReport report = new AiReport();
        report.setId(reportId);
        report.setStatus(AiReport.ReportStatus.PENDING);
        when(aiReportRepository.findById(reportId)).thenReturn(Optional.of(report));

        consumer.consume(jsonMessage);
        consumer.consume(jsonMessage);

        assertEquals(AiReport.ReportStatus.READY, report.getStatus());
        assertEquals("reports/x.md", report.getS3Key());
        verify(aiReportRepository, times(2)).save(report);
    }
}
