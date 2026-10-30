package com.learniq.ai.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.ai.rag.IngestionService;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RagIngestConsumer Unit Tests")
class RagIngestConsumerTest {

    @Mock private IngestionService ingestionService;

    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private RagIngestConsumer consumer;

    // ── class-analysis-topic ─────────────────────────────────────────────────

    @Test
    @DisplayName("CLASS_ANALYSIS_COMPLETED → ingestion çağrılır (String payload)")
    void consumeClassAnalysis_validJson_ingests() {
        UUID examId = UUID.randomUUID();
        UUID classroomId = UUID.randomUUID();
        String message = String.format(
                "{\"eventType\":\"CLASS_ANALYSIS_COMPLETED\",\"tenantId\":\"akademi_1\",\"examId\":\"%s\",\"classroomId\":\"%s\"}",
                examId, classroomId);

        consumer.consumeClassAnalysis(message);

        verify(ingestionService).ingestClassAnalysis("akademi_1", examId, classroomId);
    }

    @Test
    @DisplayName("CLASS_ANALYSIS_COMPLETED → ingestion çağrılır (Map payload — JsonDeserializer yolu)")
    void consumeClassAnalysis_mapPayload_ingests() {
        Map<String, Object> event = Map.of(
                "eventType", "CLASS_ANALYSIS_COMPLETED",
                "tenantId", "akademi_1",
                "examId", UUID.randomUUID().toString(),
                "classroomId", UUID.randomUUID().toString());

        consumer.consumeClassAnalysis(event);

        verify(ingestionService).ingestClassAnalysis(eq("akademi_1"), any(), any());
    }

    @Test
    @DisplayName("Farklı eventType → skip")
    void consumeClassAnalysis_otherEventType_skips() {
        consumer.consumeClassAnalysis("{\"eventType\":\"EXAM_FINALIZED\",\"tenantId\":\"akademi_1\"}");
        verifyNoInteractions(ingestionService);
    }

    @Test
    @DisplayName("tenantId eksik → IllegalArgumentException (kalıcı → DLT)")
    void consumeClassAnalysis_missingTenant_throwsIllegalArgument() {
        assertThatThrownBy(() -> consumer.consumeClassAnalysis(
                "{\"eventType\":\"CLASS_ANALYSIS_COMPLETED\",\"examId\":\""
                        + UUID.randomUUID() + "\",\"classroomId\":\"" + UUID.randomUUID() + "\"}"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(ingestionService);
    }

    @Test
    @DisplayName("examId eksik → IllegalArgumentException")
    void consumeClassAnalysis_missingExamId_throwsIllegalArgument() {
        assertThatThrownBy(() -> consumer.consumeClassAnalysis(
                "{\"eventType\":\"CLASS_ANALYSIS_COMPLETED\",\"examId\":\"\",\"tenantId\":\"akademi_1\",\"classroomId\":\""
                        + UUID.randomUUID() + "\"}"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(ingestionService);
    }

    @Test
    @DisplayName("Bozuk JSON → IllegalStateException (retry/DLT akışına düşer)")
    void consumeClassAnalysis_badJson_throwsIllegalState() {
        assertThatThrownBy(() -> consumer.consumeClassAnalysis("NOT_JSON"))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(ingestionService);
    }

    // ── teacher-observations-topic ───────────────────────────────────────────

    @Test
    @DisplayName("TEACHER_OBSERVATION_CREATED → ingestion çağrılır")
    void consumeObservation_created_ingests() {
        UUID obsId = UUID.randomUUID();
        String message = String.format(
                "{\"eventType\":\"TEACHER_OBSERVATION_CREATED\",\"tenantId\":\"akademi_1\",\"observationId\":\"%s\"}",
                obsId);

        consumer.consumeObservation(message);

        verify(ingestionService).ingestObservation("akademi_1", obsId);
    }

    @Test
    @DisplayName("TEACHER_OBSERVATION_UPDATED → ingestion çağrılır (re-embed yolu)")
    void consumeObservation_updated_ingests() {
        UUID obsId = UUID.randomUUID();
        String message = String.format(
                "{\"eventType\":\"TEACHER_OBSERVATION_UPDATED\",\"tenantId\":\"akademi_1\",\"observationId\":\"%s\"}",
                obsId);

        consumer.consumeObservation(new HashMap<>(Map.of(
                "eventType", "TEACHER_OBSERVATION_UPDATED",
                "tenantId", "akademi_1",
                "observationId", obsId.toString())));

        verify(ingestionService).ingestObservation("akademi_1", obsId);
    }

    @Test
    @DisplayName("TEACHER_OBSERVATION_DELETED → silme çağrılır")
    void consumeObservation_deleted_deletes() {
        UUID obsId = UUID.randomUUID();
        String message = String.format(
                "{\"eventType\":\"TEACHER_OBSERVATION_DELETED\",\"tenantId\":\"akademi_1\",\"observationId\":\"%s\"}",
                obsId);

        consumer.consumeObservation(message);

        verify(ingestionService).deleteObservation("akademi_1", obsId);
    }

    @Test
    @DisplayName("Bilinmeyen TEACHER_OBSERVATION_* eventType → hiçbir işlem yapılmaz")
    void consumeObservation_unknownSubtype_skips() {
        consumer.consumeObservation(
                "{\"eventType\":\"TEACHER_OBSERVATION_FUTURE\",\"tenantId\":\"akademi_1\",\"observationId\":\""
                        + UUID.randomUUID() + "\"}");
        verifyNoInteractions(ingestionService);
    }

    @Test
    @DisplayName("observationId eksik → IllegalArgumentException")
    void consumeObservation_missingId_throwsIllegalArgument() {
        assertThatThrownBy(() -> consumer.consumeObservation(
                "{\"eventType\":\"TEACHER_OBSERVATION_CREATED\",\"tenantId\":\"akademi_1\"}"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(ingestionService);
    }
}
