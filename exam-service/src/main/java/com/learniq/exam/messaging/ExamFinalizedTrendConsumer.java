package com.learniq.exam.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.exam.service.ClassSubjectTrendCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * exam-results-topic'i "exam-trend-group" consumer group'u ile dinler.
 * EXAM_FINALIZED event'inde class_subject_trends tablosunu doldurur.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExamFinalizedTrendConsumer {

    private final ClassSubjectTrendCalculator trendCalculator;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "exam-results-topic", groupId = "exam-trend-group")
    public void consume(String message) {
        try {
            Map<String, Object> event = objectMapper.readValue(message, new TypeReference<>() {});
            String eventType = (String) event.get("eventType");

            if (!"EXAM_FINALIZED".equals(eventType)) {
                return;
            }

            String examIdStr = (String) event.get("examId");
            String tenantId  = (String) event.get("tenantId");

            if (examIdStr == null || tenantId == null) {
                log.warn("EXAM_FINALIZED event'inde eksik alan: {}", message);
                return;
            }

            UUID examId = UUID.fromString(examIdStr);
            log.info("EXAM_FINALIZED alındı (trend-group): examId={}, tenantId={}", examId, tenantId);
            trendCalculator.calculate(tenantId, examId);

        } catch (JsonProcessingException e) {
            log.error("Kafka mesajı parse edilemedi (trend consumer): {}", message, e);
        } catch (Exception e) {
            log.error("Trend hesaplamada hata: {}", e.getMessage(), e);
        }
    }
}
