package com.learniq.exam.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.common.tenant.TenantContext;
import com.learniq.exam.service.BadgeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExamFinalizedBadgeConsumer Unit Tests")
class ExamFinalizedBadgeConsumerTest {

    @Mock private BadgeService badgeService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private ExamFinalizedBadgeConsumer consumer;

    @Test
    @DisplayName("EXAM_FINALIZED event'inde tenant bağlanır ve rozetler değerlendirilir")
    void consume_examFinalizedEvent_evaluatesBadgesWithTenant() {
        UUID examId = UUID.randomUUID();
        String message = String.format(
                "{\"eventType\":\"EXAM_FINALIZED\",\"tenantId\":\"tenant_abc\",\"examId\":\"%s\"}",
                examId);

        consumer.consume(message);

        verify(badgeService).evaluateBadgesForExam(examId);
        assertEquals(TenantContext.DEFAULT_TENANT, TenantContext.getCurrentTenant());
    }

    @Test
    @DisplayName("tenantId eksi/geçersiz rozet değerlendirme çağrılmaz ve context temiz kalır")
    void consume_missingTenantId_skips() {
        String message = "{\"eventType\":\"EXAM_FINALIZED\",\"examId\":\"" + UUID.randomUUID() + "\"}";

        consumer.consume(message);

        verifyNoInteractions(badgeService);
        assertEquals(TenantContext.DEFAULT_TENANT, TenantContext.getCurrentTenant());
    }

    @Test
    @DisplayName("Farklı eventType'larda rozet değerlendirme çağrılmaz")
    void consume_otherEventType_skips() {
        String message = "{\"eventType\":\"BADGE_AWARDED\",\"tenantId\":\"tenant_abc\",\"examId\":\""
                + UUID.randomUUID() + "\"}";

        consumer.consume(message);

        verifyNoInteractions(badgeService);
    }

    @Test
    @DisplayName("Geçersiz JSON durumunda hata fırlatmaz, context temizlenir")
    void consume_invalidJson_handlesGracefully() {
        consumer.consume("{not-a-json}");

        verifyNoInteractions(badgeService);
        assertEquals(TenantContext.DEFAULT_TENANT, TenantContext.getCurrentTenant());
    }
}
