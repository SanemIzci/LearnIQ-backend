package com.learniq.ai.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.ai.rag.IngestionService;
import com.learniq.common.messaging.RagEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * RAG ingestion consumer'ları.
 *
 * Event zarfı (schema v2): eventId, eventType, tenantId, aggregateId,
 * aggregateVersion, occurredAt, payload. Sıralama aggregateVersion ile yapılır;
 * legacy (zarfsız) mesajlar geriye dönük uyumluluk için version=0 kabul edilir.
 *
 * 1) class-analysis-topic:
 *    - CLASS_ANALYSIS_COMPLETED → sayısal fact ingestion
 *    - EXAM_DELETED / CLASSROOM_DELETED → reconciliation (tombstone)
 * 2) teacher-observations-topic:
 *    - TEACHER_OBSERVATION_CREATED/UPDATED → semantik ingestion
 *    - TEACHER_OBSERVATION_DELETED → tombstone
 *
 * Kalıcı hatalar (bozuk mesaj / eksik alan / geçersiz kiracı) → IllegalArgumentException
 * → retry edilmeden DLT. Geçici hatalar 3 kez üstel retry sonrası <topic>.DLT.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagIngestConsumer {

    private final IngestionService ingestionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "class-analysis-topic",
            groupId = "rag-ingest-group",
            containerFactory = "ragListenerContainerFactory")
    public void consumeClassAnalysis(Object message) {
        try {
            Map<String, Object> event = toEventMap(message);
            String eventType = (String) event.get("eventType");

            if (eventType == null) {
                return;
            }

            switch (eventType) {
                case "CLASS_ANALYSIS_COMPLETED" -> handleClassAnalysisCompleted(event);
                case "EXAM_DELETED" -> handleExamDeleted(event);
                case "CLASSROOM_DELETED" -> handleClassroomDeleted(event);
                default -> log.debug("class-analysis-topic: bilinmeyen eventType, atlandı: {}", eventType);
            }

        } catch (IllegalArgumentException e) {
            log.error("class-analysis-topic parametre hatası (kalıcı → DLT): {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("class-analysis-topic işlenemedi (retry → DLT): {}", e.getMessage());
            throw (e instanceof RuntimeException re) ? re : new IllegalStateException(e);
        }
    }

    @KafkaListener(
            topics = "teacher-observations-topic",
            groupId = "rag-ingest-group",
            containerFactory = "ragListenerContainerFactory")
    public void consumeObservation(Object message) {
        try {
            Map<String, Object> event = toEventMap(message);
            String eventType = (String) event.get("eventType");

            if (eventType == null || !eventType.startsWith("TEACHER_OBSERVATION_")) {
                return;
            }

            String tenantSchema = requireTenant(event);
            long version = RagEvent.aggregateVersionOf(event);
            Map<String, Object> payload = RagEvent.payloadOf(event);
            UUID observationId = requireUuid(payload, "observationId");

            switch (eventType) {
                case "TEACHER_OBSERVATION_CREATED", "TEACHER_OBSERVATION_UPDATED" ->
                        ingestionService.ingestObservation(tenantSchema, observationId, version);
                case "TEACHER_OBSERVATION_DELETED" ->
                        ingestionService.deleteObservation(tenantSchema, observationId, version);
                default -> log.info("Bilinmeyen observation eventType, atlandı: {}", eventType);
            }

        } catch (IllegalArgumentException e) {
            log.error("teacher-observations-topic parametre hatası (kalıcı → DLT): {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("teacher-observations-topic işlenemedi (retry → DLT): {}", e.getMessage());
            throw (e instanceof RuntimeException re) ? re : new IllegalStateException(e);
        }
    }

    // ── Handlers ─────────────────────────────────────────────────────────────

    private void handleClassAnalysisCompleted(Map<String, Object> event) {
        String tenantSchema = requireTenant(event);
        long version = RagEvent.aggregateVersionOf(event);
        Map<String, Object> payload = RagEvent.payloadOf(event);

        UUID examId = requireUuid(payload, "examId");
        UUID classroomId = requireUuid(payload, "classroomId");

        log.info("CLASS_ANALYSIS_COMPLETED alındı: tenant={}, examId={}, classroomId={}, version={}",
                tenantSchema, examId, classroomId, version);

        ingestionService.ingestClassAnalysis(tenantSchema, examId, classroomId, version);
    }

    private void handleExamDeleted(Map<String, Object> event) {
        String tenantSchema = requireTenant(event);
        long version = RagEvent.aggregateVersionOf(event);
        Map<String, Object> payload = RagEvent.payloadOf(event);
        UUID examId = requireUuid(payload, "examId");

        log.info("EXAM_DELETED alındı: tenant={}, examId={}, version={}", tenantSchema, examId, version);
        ingestionService.deleteExam(tenantSchema, examId, version);
    }

    private void handleClassroomDeleted(Map<String, Object> event) {
        String tenantSchema = requireTenant(event);
        long version = RagEvent.aggregateVersionOf(event);
        Map<String, Object> payload = RagEvent.payloadOf(event);
        UUID classroomId = requireUuid(payload, "classroomId");

        log.info("CLASSROOM_DELETED alındı: tenant={}, classroomId={}, version={}",
                tenantSchema, classroomId, version);
        ingestionService.deleteClassroom(tenantSchema, classroomId, version);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> toEventMap(Object message) {
        if (message instanceof String s) {
            try {
                return objectMapper.readValue(s, new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                // Bozuk mesaj kalıcıdır: retry edilmeden DLT'ye gitmeli.
                throw new IllegalArgumentException("Bozuk event JSON'u", e);
            }
        }
        if (message instanceof Map<?, ?> raw) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) raw;
            return casted;
        }
        return objectMapper.convertValue(message, new TypeReference<>() {});
    }

    private String requireTenant(Map<String, Object> event) {
        String tenant = (String) event.get("tenantId");
        if (tenant == null || tenant.isBlank()) {
            throw new IllegalArgumentException("Event'te tenantId eksik");
        }
        return tenant;
    }

    private UUID requireUuid(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Event'te '" + field + "' eksik");
        }
        return UUID.fromString(value.toString());
    }
}
