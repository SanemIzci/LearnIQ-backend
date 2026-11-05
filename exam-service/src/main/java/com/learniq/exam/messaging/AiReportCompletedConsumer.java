package com.learniq.exam.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.exam.domain.AiReport;
import com.learniq.exam.repository.AiReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiReportCompletedConsumer {

    private final AiReportRepository aiReportRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "ai-report-completed", groupId = "exam-service-group")
    public void consume(String message) {
        log.info("AI Service'den rapor tamamlandı eventi alındı: {}", message);
        try {
            Map<String, Object> event = objectMapper.readValue(message, new TypeReference<>() {});
            
            String reportIdStr = (String) event.get("reportId");
            if (reportIdStr == null) {
                log.warn("Eksik veri: reportId is null");
                return;
            }

            UUID reportId = UUID.fromString(reportIdStr);
            String statusStr = (String) event.get("status");
            String s3Key = (String) event.get("s3Key");

            boolean isFailed = "FAILED".equalsIgnoreCase(statusStr);
            boolean isProcessing = "PROCESSING".equalsIgnoreCase(statusStr);

            if (!isFailed && !isProcessing && s3Key == null) {
                log.warn("Eksik veri: reportId={}, s3Key={}", reportIdStr, s3Key);
                return;
            }

            Optional<AiReport> reportOpt = aiReportRepository.findById(reportId);
            if (reportOpt.isEmpty()) {
                log.warn("Veritabanında Rapor (ID: {}) bulunamadı.", reportId);
                return;
            }

            AiReport report = reportOpt.get();

            // 1. Başarısızlık durumu: güvenli hata mesajı kaydedilir, hassas veri sızdırılmaz
            if (isFailed) {
                String error = (String) event.get("error");
                report.setStatus(AiReport.ReportStatus.FAILED);
                report.setSummary(error != null && !error.isBlank() ? error : "Rapor oluşturulamadı.");
                report.setCompletedAt(LocalDateTime.now());
                aiReportRepository.save(report);
                log.warn("Rapor (ID: {}) FAILED olarak güncellendi.", reportId);
                return;
            }

            // 2. İşleniyor durumu
            if (isProcessing) {
                report.setStatus(AiReport.ReportStatus.PROCESSING);
                aiReportRepository.save(report);
                log.info("Rapor (ID: {}) PROCESSING olarak güncellendi.", reportId);
                return;
            }

            // 3. Başarılı tamamlanma durumu
            String summary = (String) event.get("summary");
            report.setS3Key(s3Key);
            report.setSummary(summary);
            if ("COMPLETED".equalsIgnoreCase(statusStr)) {
                report.setStatus(AiReport.ReportStatus.COMPLETED);
            } else {
                report.setStatus(AiReport.ReportStatus.READY);
            }
            report.setCompletedAt(LocalDateTime.now());
            aiReportRepository.save(report);
            log.info("Rapor (ID: {}) {} olarak güncellendi.", reportId, report.getStatus());
            
        } catch (JsonProcessingException e) {
            log.error("Event parse edilemedi: {}", message, e);
        } catch (Exception e) {
            log.error("Rapor tamamlanırken beklenmeyen hata oluştu", e);
        }
    }
}
