package com.learniq.ai.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.ai.service.ReportGenerationService;
import com.learniq.common.event.AiReportRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiReportConsumer {

    private final ReportGenerationService reportGenerationService;
    private final ObjectMapper objectMapper;

    /**
     * Handles student overall trend report generation (Yol B)
     */
    @KafkaListener(topics = "ai-report-requested-topic", groupId = "ai-service-group")
    public void consume(AiReportRequestedEvent event) {
        log.info("Received AiReportRequestedEvent for studentId: {}", event.getStudentId());
        try {
            reportGenerationService.generateAndSaveReport(event);
        } catch (Exception e) {
            log.error("Failed to generate AI trend report for student: {}", event.getStudentId(), e);
        }
    }

    /**
     * Handles on-demand exam AI report generation (Yol A)
     */
    @KafkaListener(topics = "ai-requests-topic", groupId = "ai-service-group")
    public void consumeExamReportRequest(Object eventObj) {
        log.info("Received on-demand exam AI report request: {}", eventObj);
        try {
            Map<String, Object> event;
            if (eventObj instanceof String s) {
                event = objectMapper.readValue(s, new TypeReference<>() {});
            } else if (eventObj instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) m;
                event = casted;
            } else {
                event = objectMapper.convertValue(eventObj, new TypeReference<>() {});
            }
            reportGenerationService.generateAndSaveExamReport(event);
        } catch (Exception e) {
            log.error("Failed to generate on-demand AI exam report: {}", eventObj, e);
        }
    }
}
