package com.learniq.ai.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.ai.service.ReportGenerationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
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
}
