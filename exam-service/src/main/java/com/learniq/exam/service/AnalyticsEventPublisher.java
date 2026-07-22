package com.learniq.exam.service;

import com.learniq.common.dto.StudentTrendReportDto;
import com.learniq.common.event.AiReportRequestedEvent;
import com.learniq.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishAiReportRequest(UUID studentId, StudentTrendReportDto trendData) {
        String tenantId = TenantContext.getCurrentTenant();
        
        AiReportRequestedEvent event = AiReportRequestedEvent.builder()
                .tenantId(tenantId)
                .studentId(studentId)
                .trendData(trendData)
                .build();

        kafkaTemplate.send("ai-report-requested-topic", studentId.toString(), event);
        log.info("Published AiReportRequestedEvent for studentId: {} in tenant: {}", studentId, tenantId);
    }
}
