package com.learniq.ai.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.ai.rag.IngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * RAG ingestion consumer'ları.
 *
 * 1) class-analysis-topic      → CLASS_ANALYSIS_COMPLETED (exam-service Faz 3) → sayısal fact ingestion
 * 2) teacher-observations-topic → TEACHER_OBSERVATION_CREATED/UPDATED/DELETED → semantik ingestion
 *
 * containerFactory = ragListenerContainerFactory → 3 kez üstel retry sonrası <topic>.DLT
 * (kalıcı hatalar: bozuk mesaj / eksik alan / geçersiz kiracı → anında DLT).
 *
 * Not: JsonDeserializer deserialized değeri String yerine Map olarak da verebilir;
 * bu yüzden payload tip-belirsiz (Object) işlenir.
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

            if (!"CLASS_ANALYSIS_COMPLETED".equals(event.get("eventType"))) {
                return;
            }

            UUID examId = requireUuid(event, "examId");
            UUID classroomId = requireUuid(event, "classroomId");
            String tenantSchema = requireTenant(event);

            log.info("CLASS_ANALYSIS_COMPLETED alındı: tenant={}, examId={}, classroomId={}",
                    tenantSchema, examId, classroomId);

            ingestionService.ingestClassAnalysis(tenantSchema, examId, classroomId);

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
            UUID observationId = requireUuid(event, "observationId");

            switch (eventType) {
                case "TEACHER_OBSERVATION_CREATED", "TEACHER_OBSERVATION_UPDATED" ->
                        ingestionService.ingestObservation(tenantSchema, observationId);
                case "TEACHER_OBSERVATION_DELETED" ->
                        ingestionService.deleteObservation(tenantSchema, observationId);
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

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> toEventMap(Object message) throws JsonProcessingException {
        if (message instanceof String s) {
            return objectMapper.readValue(s, new TypeReference<>() {});
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

    private UUID requireUuid(Map<String, Object> event, String field) {
        Object value = event.get(field);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Event'te '" + field + "' eksik");
        }
        return UUID.fromString(value.toString());
    }
}
