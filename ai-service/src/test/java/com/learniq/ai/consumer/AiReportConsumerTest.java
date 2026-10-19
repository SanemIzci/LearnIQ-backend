package com.learniq.ai.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.ai.service.ReportGenerationService;
import com.learniq.common.event.AiReportRequestedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiReportConsumerTest {

    @Mock
    private ReportGenerationService reportGenerationService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AiReportConsumer aiReportConsumer;

    @Test
    @DisplayName("consumeExamReportRequest - Consumes Map payload and delegates to reportGenerationService")
    void consumeExamReportRequest_mapPayload_success() {
        Map<String, Object> event = Map.of(
                "reportId", "123",
                "studentId", "456",
                "payloadData", "Total:100.00"
        );

        aiReportConsumer.consumeExamReportRequest(event);

        verify(reportGenerationService).generateAndSaveExamReport(event);
    }

    @Test
    @DisplayName("consumeExamReportRequest - Consumes JSON String payload and delegates to reportGenerationService")
    void consumeExamReportRequest_stringPayload_success() {
        String jsonPayload = "{\"reportId\":\"123\",\"studentId\":\"456\",\"payloadData\":\"Total:100.00\"}";

        aiReportConsumer.consumeExamReportRequest(jsonPayload);

        verify(reportGenerationService).generateAndSaveExamReport(anyMap());
    }

    @Test
    @DisplayName("consume(AiReportRequestedEvent) - trend raporu üretimine delege eder")
    void consume_aiReportRequestedEvent_delegates() {
        AiReportRequestedEvent event = AiReportRequestedEvent.builder()
                .tenantId("t1")
                .studentId(UUID.randomUUID())
                .build();

        aiReportConsumer.consume(event);

        verify(reportGenerationService).generateAndSaveReport(event);
    }

    @Test
    @DisplayName("consume(AiReportRequestedEvent) - servis hata fırlatsa bile dışarı sızdırmaz")
    void consume_aiReportRequestedEvent_serviceThrows_doesNotPropagate() {
        AiReportRequestedEvent event = AiReportRequestedEvent.builder()
                .studentId(UUID.randomUUID())
                .build();
        doThrow(new RuntimeException("boom")).when(reportGenerationService).generateAndSaveReport(any());

        assertDoesNotThrow(() -> aiReportConsumer.consume(event));
    }

    @Test
    @DisplayName("consumeExamReportRequest - Map/String dışı nesne convertValue ile map'e çevrilir")
    void consumeExamReportRequest_pojoPayload_convertsToMap() {
        aiReportConsumer.consumeExamReportRequest(new Payload());

        verify(reportGenerationService).generateAndSaveExamReport(anyMap());
    }

    @Test
    @DisplayName("consumeExamReportRequest - servis hata fırlatsa bile dışarı sızdırmaz")
    void consumeExamReportRequest_serviceThrows_doesNotPropagate() {
        doThrow(new RuntimeException("boom")).when(reportGenerationService).generateAndSaveExamReport(anyMap());

        assertDoesNotThrow(() -> aiReportConsumer.consumeExamReportRequest(new HashMap<>(Map.of("reportId", "1"))));
    }

    static class Payload {
        public String reportId = "1";
        public String studentId = "2";
        public String payloadData = "Total:1.0";
    }
}
