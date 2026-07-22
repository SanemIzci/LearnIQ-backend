package com.learniq.ai.consumer;

import com.learniq.ai.service.ReportGenerationService;
import com.learniq.common.event.AiReportRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiReportConsumer {

    private final ReportGenerationService reportGenerationService;

    @KafkaListener(topics = "ai-report-requested-topic", groupId = "ai-service-group")
    public void consume(AiReportRequestedEvent event) {
        log.info("Received AiReportRequestedEvent for studentId: {}", event.getStudentId());
        try {
            reportGenerationService.generateAndSaveReport(event);
        } catch (Exception e) {
            log.error("Failed to generate AI report for student: {}", event.getStudentId(), e);
        }
    }
}
