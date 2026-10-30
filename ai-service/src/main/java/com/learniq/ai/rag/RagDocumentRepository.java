package com.learniq.ai.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.common.tenant.TenantSchemaValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * rag_documents tablosuna (kiracı şeması içinde) erişim.
 *
 * - Kiracı şeması her metotta TenantSchemaValidator ile doğrulanır; tablo adları
 *   doğrulanmış şema adıyla NİTELİKLİ yazılır. SET search_path havuz bağlantı
 *   yapısında bağlantı değişince etkisini yitirebildiği için nitelikli ad,
 *   kiracılar arasındaki sızıntı riskini kökten ortadan kaldırır.
 * - Idempotency: UNIQUE(source_type, source_id) + content_hash (SHA-256).
 *   Mevcut hash == yeni hash ise embedding API'ye hiç çağrı yapılmaz.
 * - embedding sütunu V5'te koşullu eklendiği için (pgvector aktifse) varlığı kontrol edilir.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RagDocumentRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /** Mevcut dokümanın content_hash'i (yoksa null). */
    public String findHashBySource(String tenantSchema, String sourceType, UUID sourceId) {
        List<String> hashes = jdbcTemplate.queryForList(
                """
                SELECT content_hash FROM %s.rag_documents
                WHERE source_type = ? AND source_id = ?
                """.formatted(TenantSchemaValidator.requireValid(tenantSchema)),
                String.class, sourceType, sourceId);
        return hashes.isEmpty() ? null : hashes.get(0);
    }

    /** embedding sütununun (ve pgvector'ın) bu tenant şemasında aktif olup olmadığı. */
    public boolean embeddingColumnExists(String tenantSchema) {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.columns
                    WHERE table_schema = ? AND table_name = 'rag_documents' AND column_name = 'embedding'
                )
                """,
                Boolean.class, tenantSchema);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Dokümanı upsert eder (ON CONFLICT source_type, source_id DO UPDATE).
     * Embedding üretilemezse sütun koşullu olduğu için içeriği metin olarak kaydeder.
     */
    public void upsert(FactDocumentFactory.FactDocument doc, String tenantSchema, List<Double> embedding) {
        String metadataJson = serializeMetadata(doc.metadata());
        String validated = TenantSchemaValidator.requireValid(tenantSchema);
        boolean vectorAvailable = embeddingColumnExists(tenantSchema);

        if (vectorAvailable && embedding != null && !embedding.isEmpty()) {
            jdbcTemplate.update(
                    """
                    INSERT INTO %s.rag_documents
                        (source_type, source_id, classroom_id, subject_id, content, content_hash, metadata, embedding)
                    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::vector)
                    ON CONFLICT (source_type, source_id) DO UPDATE SET
                        classroom_id = EXCLUDED.classroom_id,
                        subject_id   = EXCLUDED.subject_id,
                        content      = EXCLUDED.content,
                        content_hash = EXCLUDED.content_hash,
                        metadata     = EXCLUDED.metadata,
                        embedding    = EXCLUDED.embedding
                    """.formatted(validated),
                    doc.sourceType(), doc.sourceId(),
                    doc.classroomId(), doc.subjectId(),
                    doc.content(), FactDocumentFactory.sha256Hex(doc.content()),
                    metadataJson, toVectorLiteral(embedding));
        } else {
            if (vectorAvailable && (embedding == null || embedding.isEmpty())) {
                log.warn("Embedding üretilemedi; doküman embedding'siz kaydediliyor. source={}/{}",
                        doc.sourceType(), doc.sourceId());
            } else {
                log.info("pgvector bu şemada aktif değil; doküman embedding'siz kaydediliyor. schema={}",
                        tenantSchema);
            }
            jdbcTemplate.update(
                    """
                    INSERT INTO %s.rag_documents
                        (source_type, source_id, classroom_id, subject_id, content, content_hash, metadata)
                    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
                    ON CONFLICT (source_type, source_id) DO UPDATE SET
                        classroom_id = EXCLUDED.classroom_id,
                        subject_id   = EXCLUDED.subject_id,
                        content      = EXCLUDED.content,
                        content_hash = EXCLUDED.content_hash,
                        metadata     = EXCLUDED.metadata,
                        embedding    = NULL
                    """.formatted(validated),
                    doc.sourceType(), doc.sourceId(),
                    doc.classroomId(), doc.subjectId(),
                    doc.content(), FactDocumentFactory.sha256Hex(doc.content()),
                    metadataJson);
        }
    }

    /** Kaynağın dokümanını siler (gözlem notu silindiğinde vektör deposu güncel kalır). */
    public int deleteBySource(String tenantSchema, String sourceType, UUID sourceId) {
        return jdbcTemplate.update(
                """
                DELETE FROM %s.rag_documents WHERE source_type = ? AND source_id = ?
                """.formatted(TenantSchemaValidator.requireValid(tenantSchema)),
                sourceType, sourceId);
    }

    // ── Faz 5: Admin RAG chat retrieval ──────────────────────────────────────

    /** Vektör arama sonucu: içerik + parse edilmiş metadata + cosine benzerlik. */
    public record RagHit(
            String sourceType,
            UUID sourceId,
            String content,
            Map<String, Object> metadata,
            double similarity) {}

    /**
     * Vektör benzerliğine göre arama. SQL, FAIL-SAFE olarak şema adı ile <b>nitelikli</b>
     * tablo kullanır (SET search_path havuz bağlantı yapısında zayıf kaldığı için);
     * tenant schema adı yine de önce TenantSchemaValidator ile doğrulanır.
     * Önceden filtreleme: classroom_id / subject_id (opsiyonel) → sonra cosine benzerlik.
     *
     * @return topK adede kadar hit (benzerlik DESC); threshold filtresi çağıran serviste yapılır.
     */
    public List<RagHit> search(String tenantSchema, List<Double> queryEmbedding,
                               UUID classroomId, UUID subjectId, int topK) {
        String validated = TenantSchemaValidator.requireValid(tenantSchema);
        String sql = """
                SELECT source_type, source_id, content, metadata::text AS metadata,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM %s.rag_documents
                WHERE embedding IS NOT NULL %s%s
                ORDER BY similarity DESC
                LIMIT ?
                """.formatted(validated,
                classroomId != null ? "AND classroom_id = ? " : "",
                subjectId != null ? "AND subject_id = ? " : "");

        List<Object> params = new ArrayList<>();
        params.add(toVectorLiteral(queryEmbedding));
        if (classroomId != null) params.add(classroomId);
        if (subjectId != null) params.add(subjectId);
        params.add(topK);

        List<RagHit> hits = new ArrayList<>();
        jdbcTemplate.query(sql, rs -> {
            String sourceType = rs.getString("source_type");
            UUID sourceId = UUID.fromString(rs.getString("source_id"));
            String content = rs.getString("content");
            Map<String, Object> metadata = parseMetadata(rs.getString("metadata"));
            double similarity = rs.getDouble("similarity");
            hits.add(new RagHit(sourceType, sourceId, content, metadata, similarity));
        }, params.toArray());
        return hits;
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException e) {
            log.error("Metadata serialize edilemedi; boş metadata yazılıyor: {}", e.getMessage());
            return "{}";
        }
    }

    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.error("Metadata parse edilemedi; boş metadata döndürülüyor: {}", e.getMessage());
            return Map.of();
        }
    }

    /** pgvector metin literal: '[0.0123,0.2345,...]' — performans için 6 hane hassasiyet. */
    private String toVectorLiteral(List<Double> embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format(java.util.Locale.ROOT, "%.6f", embedding.get(i)));
        }
        return sb.append("]").toString();
    }
}
