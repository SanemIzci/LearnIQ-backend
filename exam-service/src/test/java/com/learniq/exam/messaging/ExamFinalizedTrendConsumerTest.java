package com.learniq.exam.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.exam.service.ClassSubjectTrendCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExamFinalizedTrendConsumer Unit Tests")
class ExamFinalizedTrendConsumerTest {

    @Mock private ClassSubjectTrendCalculator trendCalculator;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private ExamFinalizedTrendConsumer consumer;

    @Test
    @DisplayName("EXAM_FINALIZED event'inde trendCalculator.calculate çağrılır")
    void consume_examFinalizedEvent_callsCalculator() throws Exception {
        UUID examId = UUID.randomUUID();
        String message = String.format(
                "{\"eventType\":\"EXAM_FINALIZED\",\"tenantId\":\"tenant_abc\",\"examId\":\"%s\"}",
                examId);

        consumer.consume(message);

        verify(trendCalculator).calculate("tenant_abc", examId);
    }

    @Test
    @DisplayName("Farklı eventType'larda trendCalculator çağrılmaz")
    void consume_otherEventType_skips() throws Exception {
        String message = "{\"eventType\":\"BADGE_AWARDED\",\"tenantId\":\"tenant_abc\",\"examId\":\"" + UUID.randomUUID() + "\"}";

        consumer.consume(message);

        verifyNoInteractions(trendCalculator);
    }

    @Test
    @DisplayName("tenantId eksikse trendCalculator çağrılmaz")
    void consume_missingTenantId_skips() throws Exception {
        String message = "{\"eventType\":\"EXAM_FINALIZED\",\"examId\":\"" + UUID.randomUUID() + "\"}";

        consumer.consume(message);

        verifyNoInteractions(trendCalculator);
    }

    @Test
    @DisplayName("examId eksikse trendCalculator çağrılmaz")
    void consume_missingExamId_skips() throws Exception {
        String message = "{\"eventType\":\"EXAM_FINALIZED\",\"tenantId\":\"tenant_abc\"}";

        consumer.consume(message);

        verifyNoInteractions(trendCalculator);
    }

    @Test
    @DisplayName("Geçersiz JSON gelince exception fırlatılmaz")
    void consume_invalidJson_doesNotThrow() {
        consumer.consume("NOT_JSON");
        verifyNoInteractions(trendCalculator);
    }
}
