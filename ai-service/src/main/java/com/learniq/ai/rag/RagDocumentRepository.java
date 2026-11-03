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
 *   doğrulanmış şema adıyla NİTELİKLİ yazılır.
 * - Sıralama/atomiklik: aggregateVersion DB seviyesinde uygulanır
 *   (conditional UPSERT/DELETE). Bayat yazım veritabanı tarafından reddedilir.
 * - Silme tombstone (deleted_at) ile yapılır; fiziksel DELETE yoktur, böylece
 *   DELETE sonrası gelen bayat UPDATE dokümanı yeniden oluşturamaz.
 * - Idempotency: UNIQUE(source_type, source_id) + content_hash (SHA-256) +
 *   embedding_model imzası. Hash ve model aynıysa embedding çağrısı yapılmaz.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RagDocumentRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /** Dokümanın mevcut durumu (hash + version + tombstone + model imzası). */
    public record DocState(String contentHash, long version, boolean deleted, String embeddingModel) {}

    /** Mevcut doküman durumu (yoksa null). */
    public DocState findStateBySource(String tenantSchema, String sourceType, UUID sourceId) {
        List<DocState> states = jdbcTemplate.query(
                """
                SELECT content_hash, version, deleted_at, embedding_model
                FROM %s.rag_documents
                WHERE source_type = ? AND source_id = ?
                """.formatted(TenantSchemaValidator.requireValid(tenantSchema)),
                (rs, rowNum) -> new DocState(
                        rs.getString("content_hash"),
                        rs.getLong("version"),
                        rs.getTimestamp("deleted_at") != null,
                        rs.getString("embedding_model")),
                sourceType, sourceId);
        return states.isEmpty() ? null : states.get(0);
    }

    /** Geriye dönük uyumluluk: yalnız hash okur. */
    public String findHashBySource(String tenantSchema, String sourceType, UUID sourceId) {
        DocState state = findStateBySource(tenantSchema, sourceType, sourceId);
        return state == null ? null : state.contentHash();
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
     * Version-aware upsert. DB koşulu: yalnız saklanan version <= gelen version ise yazar.
     * Böylece eşzamanlı yazımlarda en yüksek version kazanır; bayat yazım reddedilir.
     */
    public void upsert(FactDocumentFactory.FactDocument doc, String tenantSchema, long version,
                       String embeddingModel, Integer embeddingDims, List<Double> embedding) {
        doUpsert(doc, tenantSchema, version, embeddingModel, embeddingDims, embedding, false);
    }

    /**
     * Kaynak fact satırı hâlâ canlı değilse yazımı atlayan upsert.
     *
     * Silme yarışına karşı koruma INSERT ifadesinin İÇİNDEDİR
     * (INSERT ... SELECT ... WHERE EXISTS); böylece ayrı bir "önce kontrol et, sonra yaz"
     * penceresi oluşmaz. Sınav/sınıf silinmiş veya gözlem kaldırılmışsa gecikmiş bir
     * ingestion event'i dokümanı yeniden canlandıramaz.
     */
    public void upsertGuarded(FactDocumentFactory.FactDocument doc, String tenantSchema, long version,
                              String embeddingModel, Integer embeddingDims, List<Double> embedding) {
        doUpsert(doc, tenantSchema, version, embeddingModel, embeddingDims, embedding, true);
    }

    private void doUpsert(FactDocumentFactory.FactDocument doc, String tenantSchema, long version,
                          String embeddingModel, Integer embeddingDims, List<Double> embedding,
                          boolean requireLiveSource) {
        String validated = TenantSchemaValidator.requireValid(tenantSchema);
        String metadataJson = serializeMetadata(doc.metadata());
        boolean vectorAvailable = embeddingColumnExists(tenantSchema);
        String guard = requireLiveSource ? liveSourceGuard(validated, doc) : "TRUE";
        Object[] guardArgs = requireLiveSource ? liveSourceGuardArgs(doc) : new Object[0];

        if (vectorAvailable && embedding != null && !embedding.isEmpty()) {
            jdbcTemplate.update(
                    """
                    INSERT INTO %1$s.rag_documents AS rd
                        (source_type, source_id, classroom_id, subject_id, exam_id, content, content_hash,
                         metadata, embedding, version, embedding_model, embedding_dims, deleted_at)
                    SELECT ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::vector, ?, ?, ?, NULL
                    WHERE %2$s
                    ON CONFLICT (source_type, source_id) DO UPDATE SET
                        classroom_id    = EXCLUDED.classroom_id,
                        subject_id      = EXCLUDED.subject_id,
                        exam_id         = EXCLUDED.exam_id,
                        content         = EXCLUDED.content,
                        content_hash    = EXCLUDED.content_hash,
                        metadata        = EXCLUDED.metadata,
                        embedding       = EXCLUDED.embedding,
                        embedding_model = EXCLUDED.embedding_model,
                        embedding_dims  = EXCLUDED.embedding_dims,
                        version         = EXCLUDED.version,
                        deleted_at      = NULL
                    WHERE rd.version <= EXCLUDED.version
                    """.formatted(validated, guard),
                    concatArgs(new Object[]{
                            doc.sourceType(), doc.sourceId(),
                            doc.classroomId(), doc.subjectId(), doc.examId(),
                            doc.content(), FactDocumentFactory.sha256Hex(doc.content()),
                            metadataJson, toVectorLiteral(embedding),
                            version, embeddingModel, embeddingDims
                    }, guardArgs));
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
                    INSERT INTO %1$s.rag_documents AS rd
                        (source_type, source_id, classroom_id, subject_id, exam_id, content, content_hash,
                         metadata, version, embedding_model, embedding_dims, deleted_at)
                    SELECT ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, NULL
                    WHERE %2$s
                    ON CONFLICT (source_type, source_id) DO UPDATE SET
                        classroom_id    = EXCLUDED.classroom_id,
                        subject_id      = EXCLUDED.subject_id,
                        exam_id         = EXCLUDED.exam_id,
                        content         = EXCLUDED.content,
                        content_hash    = EXCLUDED.content_hash,
                        metadata        = EXCLUDED.metadata,
                        embedding       = NULL,
                        embedding_model = EXCLUDED.embedding_model,
                        embedding_dims  = EXCLUDED.embedding_dims,
                        version         = EXCLUDED.version,
                        deleted_at      = NULL
                    WHERE rd.version <= EXCLUDED.version
                    """.formatted(validated, guard),
                    concatArgs(new Object[]{
                            doc.sourceType(), doc.sourceId(),
                            doc.classroomId(), doc.subjectId(), doc.examId(),
                            doc.content(), FactDocumentFactory.sha256Hex(doc.content()),
                            metadataJson, version, embeddingModel, embeddingDims
                    }, guardArgs));
        }
    }

    /** Kaynak fact satırının varlığını tek statement içinde doğrulayan guard ifadesi. */
    private String liveSourceGuard(String schema, FactDocumentFactory.FactDocument doc) {
        return switch (doc.sourceType()) {
            case FactDocumentFactory.SOURCE_CLASS_ANALYSIS -> """
                    EXISTS (SELECT 1 FROM %s.class_subject_trends src
                             WHERE src.classroom_id = ? AND src.subject_id = ? AND src.exam_id = ?)
                    """.formatted(schema);
            case FactDocumentFactory.SOURCE_OBSERVATION ->
                    "EXISTS (SELECT 1 FROM %s.teacher_observations src WHERE src.id = ?)"
                            .formatted(schema);
            default -> "TRUE";
        };
    }

    private Object[] liveSourceGuardArgs(FactDocumentFactory.FactDocument doc) {
        return switch (doc.sourceType()) {
            case FactDocumentFactory.SOURCE_CLASS_ANALYSIS ->
                    new Object[]{doc.classroomId(), doc.subjectId(), doc.examId()};
            case FactDocumentFactory.SOURCE_OBSERVATION -> new Object[]{doc.sourceId()};
            default -> new Object[0];
        };
    }

    private Object[] concatArgs(Object[] base, Object[] extra) {
        Object[] all = new Object[base.length + extra.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(extra, 0, all, base.length, extra.length);
        return all;
    }

    /**
     * Tombstone: fiziksel silme yerine deleted_at işaretler ve version'u ilerletir.
     * Yalnız saklanan version <= silme version'u ise uygulanır (bayat DELETE etkisiz).
     */
    public int tombstone(String tenantSchema, String sourceType, UUID sourceId, long version) {
        return jdbcTemplate.update(
                """
                UPDATE %s.rag_documents
                   SET deleted_at = NOW(), version = GREATEST(version, ?) + 1
                 WHERE source_type = ? AND source_id = ? AND version <= ?
                """.formatted(TenantSchemaValidator.requireValid(tenantSchema)),
                version, sourceType, sourceId, version);
    }

    /** Bir sınıfa ait tüm dokümanları tombstone'lar (classroom silme/analiz temizliği). */
    public int tombstoneByClassroom(String tenantSchema, UUID classroomId, long version) {
        return jdbcTemplate.update(
                """
                UPDATE %s.rag_documents
                   SET deleted_at = NOW(), version = GREATEST(version, ?) + 1
                 WHERE classroom_id = ? AND version <= ? AND deleted_at IS NULL
                """.formatted(TenantSchemaValidator.requireValid(tenantSchema)),
                version, classroomId, version);
    }

    /**
     * Bir sınava ait tüm class-analysis dokümanlarını tombstone'lar.
     * Yalnızca sınavdan TÜRETİLEN fact dokümanları silinir; sınava referans veren
     * gözlem notları (source_type = TEACHER_OBSERVATION) etkilenmez — bunlar sınıf
     * içeriği olarak geçerli kalır (teacher_observations.exam_id zaten SET NULL'dır).
     */
    public int tombstoneByExam(String tenantSchema, UUID examId, long version) {
        return jdbcTemplate.update(
                """
                UPDATE %s.rag_documents
                   SET deleted_at = NOW(), version = GREATEST(version, ?) + 1
                 WHERE source_type = ? AND exam_id = ? AND version <= ? AND deleted_at IS NULL
                """.formatted(TenantSchemaValidator.requireValid(tenantSchema)),
                version, FactDocumentFactory.SOURCE_CLASS_ANALYSIS, examId, version);
    }

    // ── Retrieval ─────────────────────────────────────────────────────────────

    /** Vektör arama sonucu: içerik + parse edilmiş metadata + cosine benzerlik. */
    public record RagHit(
            String sourceType,
            UUID sourceId,
            String content,
            Map<String, Object> metadata,
            double similarity,
            String embeddingModel) {}

    /**
     * Vektör benzerliğine göre arama. Tombstone'lar ve (verilmişse) izinli olmayan
     * embedding model imzaları dışlanır.
     *
     * @param allowedModels boş/null ise model filtresi uygulanmaz (geçiş uyumluluğu).
     */
    public List<RagHit> search(String tenantSchema, List<Double> queryEmbedding,
                               UUID classroomId, UUID subjectId, int topK,
                               List<String> allowedModels) {
        String validated = TenantSchemaValidator.requireValid(tenantSchema);
        StringBuilder sql = new StringBuilder("""
                SELECT source_type, source_id, content, metadata::text AS metadata,
                       1 - (embedding <=> ?::vector) AS similarity, embedding_model
                FROM %s.rag_documents
                WHERE embedding IS NOT NULL AND deleted_at IS NULL
                """.formatted(validated));

        List<Object> params = new ArrayList<>();
        params.add(toVectorLiteral(queryEmbedding));

        if (classroomId != null) {
            sql.append(" AND classroom_id = ?");
            params.add(classroomId);
        }
        if (subjectId != null) {
            sql.append(" AND subject_id = ?");
            params.add(subjectId);
        }
        if (allowedModels != null && !allowedModels.isEmpty()) {
            sql.append(" AND embedding_model IN (")
                    .append(String.join(",", java.util.Collections.nCopies(allowedModels.size(), "?")))
                    .append(")");
            params.addAll(allowedModels);
        }
        sql.append(" ORDER BY similarity DESC LIMIT ?");
        params.add(topK);

        List<RagHit> hits = new ArrayList<>();
        jdbcTemplate.query(sql.toString(), rs -> {
            String sourceType = rs.getString("source_type");
            UUID sourceId = UUID.fromString(rs.getString("source_id"));
            String content = rs.getString("content");
            Map<String, Object> metadata = parseMetadata(rs.getString("metadata"));
            double similarity = rs.getDouble("similarity");
            String model = rs.getString("embedding_model");
            hits.add(new RagHit(sourceType, sourceId, content, metadata, similarity, model));
        }, params.toArray());
        return hits;
    }

    // ── Backfill (model migration) ────────────────────────────────────────────

    /** Model imzası güncel olmayan dokümanları döndürür (batch re-embedding). */
    public List<BackfillCandidate> findBackfillCandidates(String tenantSchema, String currentModelSig, int limit) {
        String validated = TenantSchemaValidator.requireValid(tenantSchema);
        return jdbcTemplate.query(
                """
                SELECT source_type, source_id, content
                FROM %s.rag_documents
                WHERE deleted_at IS NULL
                  AND embedding IS NOT NULL
                  AND (embedding_model IS NULL OR embedding_model <> ?)
                ORDER BY created_at
                LIMIT ?
                """.formatted(validated),
                (rs, rowNum) -> new BackfillCandidate(
                        rs.getString("source_type"),
                        UUID.fromString(rs.getString("source_id")),
                        rs.getString("content")),
                currentModelSig, limit);
    }

    public record BackfillCandidate(String sourceType, UUID sourceId, String content) {}

    /** Backfill: yalnız embedding + model imzası güncellenir (version/İçerik değişmez). */
    public int updateEmbedding(String tenantSchema, String sourceType, UUID sourceId,
                               List<Double> embedding, String embeddingModel, Integer embeddingDims) {
        return jdbcTemplate.update(
                """
                UPDATE %s.rag_documents
                   SET embedding = ?::vector, embedding_model = ?, embedding_dims = ?
                 WHERE source_type = ? AND source_id = ? AND deleted_at IS NULL
                """.formatted(TenantSchemaValidator.requireValid(tenantSchema)),
                toVectorLiteral(embedding), embeddingModel, embeddingDims, sourceType, sourceId);
    }

    /** Aktif (silinmemiş) doküman sayısı — coverage metriği için. */
    public long countLive(String tenantSchema) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM %s.rag_documents WHERE deleted_at IS NULL"
                        .formatted(TenantSchemaValidator.requireValid(tenantSchema)),
                Long.class);
        return count == null ? 0 : count;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
