package com.learniq.common.messaging;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * RAG event zarfı (schema v2).
 *
 * Sıralama yalnızca {@code aggregateVersion} ile yapılır; {@code occurredAt}
 * yalnızca telemetri amaçlıdır. Zarf düz bir Map olarak taşınır (Kafka JsonSerializer).
 *
 * Legacy (zarfsız) mesajlar geriye dönük uyumluluk için kabul edilir; bu durumda
 * consumer version=0 kabul eder ve bugünkü davranış korunur.
 */
public final class RagEvent {

    public static final int SCHEMA_VERSION = 2;
    public static final String FIELD_EVENT_ID = "eventId";
    public static final String FIELD_EVENT_TYPE = "eventType";
    public static final String FIELD_SCHEMA_VERSION = "schemaVersion";
    public static final String FIELD_TENANT = "tenantId";
    public static final String FIELD_AGGREGATE_ID = "aggregateId";
    public static final String FIELD_AGGREGATE_VERSION = "aggregateVersion";
    public static final String FIELD_OCCURRED_AT = "occurredAt";
    public static final String FIELD_PAYLOAD = "payload";

    private RagEvent() {}

    /** Yeni zarf üretir. */
    public static Map<String, Object> envelope(String eventType,
                                               String tenantId,
                                               String aggregateId,
                                               long aggregateVersion,
                                               Map<String, Object> payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put(FIELD_EVENT_ID, UUID.randomUUID().toString());
        envelope.put(FIELD_SCHEMA_VERSION, SCHEMA_VERSION);
        envelope.put(FIELD_EVENT_TYPE, eventType);
        envelope.put(FIELD_TENANT, tenantId);
        envelope.put(FIELD_AGGREGATE_ID, aggregateId);
        envelope.put(FIELD_AGGREGATE_VERSION, aggregateVersion);
        envelope.put(FIELD_OCCURRED_AT, Instant.now().toString());
        envelope.put(FIELD_PAYLOAD, payload == null ? Map.of() : payload);
        return envelope;
    }

    /** Mesaj yeni zarflı mı? (aggregateVersion alanı varsa) */
    public static boolean isEnvelope(Map<String, Object> message) {
        return message != null && message.get(FIELD_AGGREGATE_VERSION) != null;
    }

    /** Zarf varsa payload'ı, yoksa mesajın kendisini (legacy) döndürür. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> payloadOf(Map<String, Object> message) {
        if (!isEnvelope(message)) {
            return message;
        }
        Object payload = message.get(FIELD_PAYLOAD);
        if (payload instanceof Map<?, ?> raw) {
            return (Map<String, Object>) raw;
        }
        return Map.of();
    }

    /** Zarf varsa aggregateVersion; legacy ise 0. */
    public static long aggregateVersionOf(Map<String, Object> message) {
        Object value = message.get(FIELD_AGGREGATE_VERSION);
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
