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
            "{\"reportId\":\"%s\",\"tenantId\":\"tenant_demo\",\"s3Key\":\"%s\",\"summary\":\"%s\"}",
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
            "{\"reportId\":\"%s\",\"tenantId\":\"tenant_demo\",\"s3Key\":\"test\"}", reportId
        );

        when(aiReportRepository.findById(reportId)).thenReturn(Optional.empty());

        consumer.consume(jsonMessage);

        verify(aiReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("consume - Eksik s3Key içeren mesaj atlanır (DB'ye hiç gidilmez)")
    void consume_missingS3Key_skips() {
        UUID reportId = UUID.randomUUID();
        String jsonMessage = String.format("{\"reportId\":\"%s\",\"tenantId\":\"tenant_demo\",\"summary\":\"x\"}", reportId);

        consumer.consume(jsonMessage);

        verify(aiReportRepository, never()).findById(any(UUID.class));
        verify(aiReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("consume - Geçersiz (UUID olmayan) reportId hata fırlatmaz, kaydetmez")
    void consume_invalidReportId_handlesGracefully() {
        String jsonMessage = "{\"reportId\":\"not-a-uuid\",\"tenantId\":\"tenant_demo\",\"s3Key\":\"reports/x.md\"}";

        consumer.consume(jsonMessage);

        verify(aiReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("consume - Aynı event iki kez gelirse (at-least-once) rapor READY kalır")
    void consume_duplicateDelivery_isIdempotent() {
        UUID reportId = UUID.randomUUID();
        String jsonMessage = String.format(
                "{\"reportId\":\"%s\",\"tenantId\":\"tenant_demo\",\"s3Key\":\"reports/x.md\",\"summary\":\"ok\"}", reportId);

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

    @Test
    @DisplayName("consume - FAILED eventi geldiğinde rapor durumu FAILED olur ve güvenli mesaj kaydedilir")
    void consume_failedStatus_updatesReportToFailed() {
        UUID reportId = UUID.randomUUID();
        String jsonMessage = String.format(
                "{\"reportId\":\"%s\",\"tenantId\":\"tenant_demo\",\"status\":\"FAILED\",\"error\":\"Rapor oluşturulurken bir hata oluştu.\"}", reportId);

        AiReport report = new AiReport();
        report.setId(reportId);
        report.setStatus(AiReport.ReportStatus.PROCESSING);
        when(aiReportRepository.findById(reportId)).thenReturn(Optional.of(report));

        consumer.consume(jsonMessage);

        assertEquals(AiReport.ReportStatus.FAILED, report.getStatus());
        assertEquals("Rapor oluşturulurken bir hata oluştu.", report.getSummary());
        verify(aiReportRepository).save(report);
    }

    @Test
    @DisplayName("consume - PROCESSING eventi geldiğinde rapor durumu PROCESSING olur")
    void consume_processingStatus_updatesReportToProcessing() {
        UUID reportId = UUID.randomUUID();
        String jsonMessage = String.format(
                "{\"reportId\":\"%s\",\"tenantId\":\"tenant_demo\",\"status\":\"PROCESSING\"}", reportId);

        AiReport report = new AiReport();
        report.setId(reportId);
        report.setStatus(AiReport.ReportStatus.PENDING);
        when(aiReportRepository.findById(reportId)).thenReturn(Optional.of(report));

        consumer.consume(jsonMessage);

        assertEquals(AiReport.ReportStatus.PROCESSING, report.getStatus());
        verify(aiReportRepository).save(report);
    }

    @Test
    @DisplayName("tenantId eksi/geçersizse DB'ye hiç gidilmez ve context temizlenir")
    void consume_missingTenantId_skipsAndClearsContext() {
        UUID reportId = UUID.randomUUID();
        String jsonMessage = String.format(
                "{\"reportId\":\"%s\",\"s3Key\":\"reports/x.md\",\"summary\":\"ok\"}", reportId);

        consumer.consume(jsonMessage);

        verify(aiReportRepository, never()).findById(any(UUID.class));
        verify(aiReportRepository, never()).save(any());
        assertEquals(com.learniq.common.tenant.TenantContext.DEFAULT_TENANT,
                com.learniq.common.tenant.TenantContext.getCurrentTenant());
    }

    @Test
    @DisplayName("Tenant'lı akıştan sonra TenantContext temizlenir (consumer thread sızıntı yok)")
    void consume_validEvent_clearsTenantContext() {
        UUID reportId = UUID.randomUUID();
        String jsonMessage = String.format(
                "{\"reportId\":\"%s\",\"tenantId\":\"tenant_demo\",\"s3Key\":\"reports/x.md\",\"summary\":\"ok\"}", reportId);

        AiReport report = new AiReport();
        report.setId(reportId);
        report.setStatus(AiReport.ReportStatus.PENDING);
        when(aiReportRepository.findById(reportId)).thenReturn(Optional.of(report));

        consumer.consume(jsonMessage);

        assertEquals(com.learniq.common.tenant.TenantContext.DEFAULT_TENANT,
                com.learniq.common.tenant.TenantContext.getCurrentTenant());
    }
}
