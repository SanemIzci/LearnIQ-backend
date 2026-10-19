package com.learniq.exam.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.exam.service.BadgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExamFinalizedBadgeConsumer {

    private final BadgeService badgeService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "exam-results-topic", groupId = "exam-badge-group")
    public void consume(String message) {
        try {
            Map<String, Object> event = objectMapper.readValue(message, new TypeReference<>() {});
            String eventType = (String) event.get("eventType");
            
            if ("EXAM_FINALIZED".equals(eventType)) {
                String examIdStr = (String) event.get("examId");
                if (examIdStr != null) {
                    UUID examId = UUID.fromString(examIdStr);
                    log.info("ExamFinalizedEvent alındı. Rozetler hesaplanıyor... Sınav ID: {}", examId);
                    badgeService.evaluateBadgesForExam(examId);
                }
            }
        } catch (JsonProcessingException e) {
            log.error("Kafka mesajı parse edilemedi: {}", message, e);
        } catch (Exception e) {
            log.error("Rozet değerlendirmesinde hata oluştu: {}", e.getMessage(), e);
        }
    }
}
