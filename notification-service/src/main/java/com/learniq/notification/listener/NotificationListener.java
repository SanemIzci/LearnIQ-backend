package com.learniq.notification.listener;

import com.learniq.common.event.ExamResultProcessedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationListener {

    @KafkaListener(topics = "exam-results-topic", groupId = "notification-group")
    public void handleExamResult(ExamResultProcessedEvent event) {
        log.info("Notification [Tenant: {}]: Exam {} results published for Student {}. Score: {}",
                event.getTenantId(),
                event.getExamId(),
                event.getStudentId(),
                event.getTotalScore());
    }
}
