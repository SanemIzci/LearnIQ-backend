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
            String s3Key = (String) event.get("s3Key");
            String summary = (String) event.get("summary");
            
            if (reportIdStr == null || s3Key == null) {
                log.warn("Eksik veri: reportId={}, s3Key={}", reportIdStr, s3Key);
                return;
            }
            
            UUID reportId = UUID.fromString(reportIdStr);
            Optional<AiReport> reportOpt = aiReportRepository.findById(reportId);
            
            if (reportOpt.isPresent()) {
                AiReport report = reportOpt.get();
                report.setS3Key(s3Key);
                report.setSummary(summary);
                report.setStatus(AiReport.ReportStatus.READY);
                report.setCompletedAt(LocalDateTime.now());
                
                aiReportRepository.save(report);
                log.info("Rapor (ID: {}) READY olarak güncellendi.", reportId);
            } else {
                log.warn("Veritabanında Rapor (ID: {}) bulunamadı.", reportId);
            }
            
        } catch (JsonProcessingException e) {
            log.error("Event parse edilemedi: {}", message, e);
        } catch (Exception e) {
            log.error("Rapor tamamlanırken beklenmeyen hata oluştu", e);
        }
    }
}
