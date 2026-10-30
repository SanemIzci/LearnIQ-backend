package com.learniq.ai.rag;

import com.learniq.common.tenant.TenantSchemaValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RAG ingestion orkestrasyonu.
 *
 * Akış: Kafka event → tenant şemasına geç → fact'leri doğrudan şemadan oku
 *       → kanonik doküman üret → SHA-256 idempotency → embedding → pgvector upsert.
 *
 * Sayısal doğruluk kuralı: Fact tamamen tenant şemasındaki class_subject_trends
 * tablosundan okunur; embedding üretimi yorum/işlem içermez.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final TenantSchemaService tenantSchemaService;
    private final RagDocumentRepository ragDocumentRepository;
    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate jdbcTemplate;

    // ── CLASS_ANALYSIS_COMPLETED ─────────────────────────────────────────────

    @Transactional
    public void ingestClassAnalysis(String tenantSchema, UUID examId, UUID classroomId) {
        tenantSchemaService.switchTo(tenantSchema);

        // Kirim analiz satırları + isim zenginleştirmesi (yorum yok, ham fact)
        // SQL, doğrulanmış şema adıyla NİTELİKLİ tablo kullanır (search_path havuzda güvenilmez).
        List<Map<String, Object>> trendRows = jdbcTemplateTrendRows(tenantSchema, examId, classroomId);
        if (trendRows.isEmpty()) {
            log.info("Trend satırı bulunamadı; ingestion atlandı. examId={}, classroomId={}", examId, classroomId);
            return;
        }

        String classroomName = queryClassroomName(tenantSchema, classroomId);
        Integer expectedCount = queryExpectedStudentCount(tenantSchema, classroomId);

        int embedded = 0;
        int skipped = 0;
        for (Map<String, Object> row : trendRows) {
            UUID subjectId = toUuid(row.get("subject_id"));
            UUID rowExamId = toUuid(row.get("exam_id"));

            FactDocumentFactory.ClassAnalysisFactRow factRow = new FactDocumentFactory.ClassAnalysisFactRow(
                    tenantSchema,
                    classroomId,
                    subjectId,
                    rowExamId,
                    classroomName,
                    (String) row.get("subject_name"),
                    (String) row.get("exam_title"),
                    toLocalDate(row.get("exam_date")),
                    (String) row.get("previous_exam_title"),
                    toBigDecimal(row.get("avg_net")),
                    toBigDecimal(row.get("avg_score")),
                    toInt(row.get("student_count")),
                    expectedCount != null ? expectedCount : 0,
                    toBigDecimal(row.get("participation_rate")),
                    toBigDecimal(row.get("delta_from_previous")),
                    (String) row.get("trend_direction"),
                    toLocalDateTime(row.get("calculated_at")));

            FactDocumentFactory.FactDocument doc = FactDocumentFactory.classAnalysisFact(factRow);

            if (ingestDoc(doc, tenantSchema)) {
                embedded++;
            } else {
                skipped++;
            }
        }
        log.info("Sınıf analizi ingestion tamamlandı: examId={} classroomId={} → embedded={}, hash-aynı-atlandı={}",
                examId, classroomId, embedded, skipped);
    }

    // ── TEACHER_OBSERVATION_CREATED / UPDATED ────────────────────────────────

    @Transactional
    public void ingestObservation(String tenantSchema, UUID observationId) {
        tenantSchemaService.switchTo(tenantSchema);

        Map<String, Object> obs;
        try {
            obs = jdbcTemplate.queryForMap(
                    """
                    SELECT o.id, o.teacher_id, o.classroom_id, o.subject_id, o.exam_id, o.content,
                           o.tags, o.created_at,
                           c.name AS classroom_name,
                           (SELECT s.name FROM %1$s.subjects s WHERE s.id = o.subject_id) AS subject_name
                    FROM %1$s.teacher_observations o
                    LEFT JOIN %1$s.classrooms c ON c.id = o.classroom_id
                    WHERE o.id = ?
                    """.formatted(TenantSchemaValidator.requireValid(tenantSchema)),
                    observationId);
        } catch (EmptyResultDataAccessException e) {
            log.info("Gözlem notu bulunamadı (silinmiş olabilir); ingestion atlandı. id={}", observationId);
            return;
        }

        FactDocumentFactory.ObservationRow row = new FactDocumentFactory.ObservationRow(
                tenantSchema,
                toUuid(obs.get("id")),
                toUuid(obs.get("teacher_id")),
                toUuid(obs.get("classroom_id")),
                toUuid(obs.get("subject_id")),
                toUuid(obs.get("exam_id")),
                (String) obs.get("classroom_name"),
                (String) obs.get("subject_name"),
                (String) obs.get("content"),
                (String) obs.get("tags"),
                toLocalDateTime(obs.get("created_at")));

        FactDocumentFactory.FactDocument doc = FactDocumentFactory.observationDocument(row);
        boolean embedded = ingestDoc(doc, tenantSchema);
        log.info("Gözlem ingestion: id={} → {}", observationId, embedded ? "indexlendi" : "hash aynı, atlandı");
    }

    // ── TEACHER_OBSERVATION_DELETED ──────────────────────────────────────────

    @Transactional
    public void deleteObservation(String tenantSchema, UUID observationId) {
        tenantSchemaService.switchTo(tenantSchema);
        int deleted = ragDocumentRepository.deleteBySource(tenantSchema, FactDocumentFactory.SOURCE_OBSERVATION, observationId);
        log.info("Gözlem dokümanı silindi: id={} → silinen satır={}", observationId, deleted);
    }

    // ── Ortak ingestion adımı (hash idempotency + embedding) ────────────────

    /**
     * @return true: yeni indexlendi/reindexlendi; false: içerik değişmedi (hash aynı) → embedding çağrılmadı.
     */
    private boolean ingestDoc(FactDocumentFactory.FactDocument doc, String tenantSchema) {
        String newHash = FactDocumentFactory.sha256Hex(doc.content());
        String existingHash = ragDocumentRepository.findHashBySource(tenantSchema, doc.sourceType(), doc.sourceId());

        if (newHash.equals(existingHash)) {
            return false;
        }

        List<Double> embedding = embeddingModel.embed(doc.content());
        ragDocumentRepository.upsert(doc, tenantSchema, embedding);
        return true;
    }

    // ── Tenant şeması fact okuma ────────────────────────────────────────────

    private List<Map<String, Object>> jdbcTemplateTrendRows(String tenantSchema, UUID examId, UUID classroomId) {
        // Not: tablolar doğrulanmış kiracı şema adıyla NİTELİKLİ yazılır
        // (SET search_path havuz bağlantı yapısında bağlantı değişince etkisini yitirir).
        return jdbcTemplate.queryForList(
                """
                SELECT cst.subject_id,
                       cst.exam_id,
                       s.name  AS subject_name,
                       e.title AS exam_title,
                       e.exam_date,
                       (SELECT prev.title
                          FROM %1$s.class_subject_trends prev_cst
                          JOIN %1$s.exams prev ON prev.id = prev_cst.exam_id
                         WHERE prev_cst.classroom_id = cst.classroom_id
                           AND prev_cst.subject_id   = cst.subject_id
                           AND prev_cst.exam_id      != cst.exam_id
                           AND prev.exam_date < (SELECT CURRENT.exam_date FROM %1$s.exams CURRENT WHERE CURRENT.id = cst.exam_id)
                         ORDER BY prev.exam_date DESC
                         LIMIT 1) AS previous_exam_title,
                       cst.avg_net,
                       cst.avg_score,
                       cst.student_count,
                       cst.participation_rate,
                       cst.delta_from_previous,
                       cst.trend_direction,
                       cst.calculated_at
                FROM %1$s.class_subject_trends cst
                JOIN %1$s.subjects s ON s.id = cst.subject_id
                JOIN %1$s.exams e ON e.id = cst.exam_id
                WHERE cst.classroom_id = ? AND cst.exam_id = ?
                ORDER BY s.name
                """.formatted(TenantSchemaValidator.requireValid(tenantSchema)),
                classroomId, examId);
    }

    private String queryClassroomName(String tenantSchema, UUID classroomId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT name FROM %s.classrooms WHERE id = ?"
                            .formatted(TenantSchemaValidator.requireValid(tenantSchema)),
                    String.class, classroomId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private Integer queryExpectedStudentCount(String tenantSchema, UUID classroomId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM %s.classroom_students WHERE classroom_id = ?"
                            .formatted(TenantSchemaValidator.requireValid(tenantSchema)),
                    Integer.class, classroomId);
        } catch (EmptyResultDataAccessException e) {
            return 0;
        }
    }

    // ── Type-safe helpers ────────────────────────────────────────────────────

    private UUID toUuid(Object val) {
        if (val == null) return null;
        return val instanceof UUID u ? u : UUID.fromString(val.toString());
    }

    private java.math.BigDecimal toBigDecimal(Object val) {
        if (val == null) return null;
        return val instanceof java.math.BigDecimal bd ? bd
                : java.math.BigDecimal.valueOf(((Number) val).doubleValue());
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        return ((Number) val).intValue();
    }

    private LocalDate toLocalDate(Object val) {
        if (val == null) return null;
        if (val instanceof LocalDate ld) return ld;
        if (val instanceof Date d) return d.toLocalDate();
        if (val instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        return LocalDate.parse(val.toString());
    }

    private LocalDateTime toLocalDateTime(Object val) {
        if (val == null) return null;
        if (val instanceof LocalDateTime ldt) return ldt;
        if (val instanceof Timestamp ts) return ts.toLocalDateTime();
        if (val instanceof Date d) return d.toLocalDate().atStartOfDay();
        return LocalDateTime.parse(val.toString());
    }
}
