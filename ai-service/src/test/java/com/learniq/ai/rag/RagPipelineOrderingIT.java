package com.learniq.ai.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.common.tenant.flyway.TenantSchemaMigrator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Gerçek PostgreSQL + pgvector üzerinde RAG sıralama / tombstone / retrieval /
 * backfill davranışlarını doğrular (F-01, F-02, F-03, F-06, F-07, F-10).
 *
 * Embedding sağlayıcısı mock'tur; DB ve SQL gerçektir.
 */
@Testcontainers
@DisplayName("RAG pipeline ordering & tombstone IT")
class RagPipelineOrderingIT {

    private static final String TENANT = "it_tenant";
    private static final int DIMS = 1536;
    private static final String MODEL = "text-embedding-3-small@1536";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    private static DataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
    private static RagDocumentRepository repository;
    private static IngestionService ingestionService;
    private static EmbeddingModel embeddingModel;
    private static RagMetrics ragMetrics;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate boot = new JdbcTemplate(dataSource);
        boot.execute("CREATE EXTENSION IF NOT EXISTS vector");
        TenantSchemaMigrator.migrateTenant(dataSource, TENANT);

        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new RagDocumentRepository(jdbcTemplate, new ObjectMapper());
        embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyString())).thenReturn(vector(0.1));
        ragMetrics = new RagMetrics(new SimpleMeterRegistry());

        TenantSchemaService tenantSchemaService = new TenantSchemaService(jdbcTemplate);
        ingestionService = new IngestionService(tenantSchemaService, repository, embeddingModel,
                jdbcTemplate, ragMetrics);
        ReflectionTestUtils.setField(ingestionService, "embeddingModelSig", MODEL);
        ReflectionTestUtils.setField(ingestionService, "embeddingDims", DIMS);
    }

    @AfterAll
    static void tearDown() {
        if (POSTGRES != null) {
            POSTGRES.stop();
        }
    }

    @BeforeEach
    void cleanDocuments() {
        jdbcTemplate.execute("SET search_path TO " + TENANT);
        jdbcTemplate.update("TRUNCATE " + TENANT + ".rag_documents");
        jdbcTemplate.update("TRUNCATE " + TENANT + ".teacher_observations CASCADE");
        reset(embeddingModel);
        when(embeddingModel.embed(anyString())).thenReturn(vector(0.1));
    }

    // ── Sıralama (out-of-order) ──────────────────────────────────────────────

    @Test
    @DisplayName("IT-1: geç gelen düşük version mevcut dokümanı ezmez")
    void staleUpdate_isRejectedByConditionalUpsert() {
        UUID sourceId = UUID.randomUUID();
        repository.upsert(doc(sourceId, "YENI içerik", null), TENANT, 2L, MODEL, DIMS, vector(0.1));
        repository.upsert(doc(sourceId, "ESKI içerik", null), TENANT, 1L, MODEL, DIMS, vector(0.2));

        RagDocumentRepository.DocState state =
                repository.findStateBySource(TENANT, FactDocumentFactory.SOURCE_OBSERVATION, sourceId);

        assertThat(state.version()).isEqualTo(2L);
        assertThat(state.contentHash()).isEqualTo(FactDocumentFactory.sha256Hex("YENI içerik"));
    }

    @Test
    @DisplayName("IT-2: aynı event iki kez geldiğinde sonuç değişmez (idempotent)")
    void duplicateEvent_isIdempotent() {
        UUID sourceId = UUID.randomUUID();
        FactDocumentFactory.FactDocument doc = doc(sourceId, "tekrar eden içerik", null);

        repository.upsert(doc, TENANT, 1L, MODEL, DIMS, vector(0.1));
        repository.upsert(doc, TENANT, 1L, MODEL, DIMS, vector(0.1));

        assertThat(repository.countLive(TENANT)).isEqualTo(1);
    }

    // ── Tombstone ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("IT-3: tombstone sonrası bayat update dokümanı diriltmez")
    void tombstone_preventsResurrectionByStaleUpdate() {
        UUID sourceId = UUID.randomUUID();
        repository.upsert(doc(sourceId, "canlı", null), TENANT, 1L, MODEL, DIMS, vector(0.1));
        repository.tombstone(TENANT, FactDocumentFactory.SOURCE_OBSERVATION, sourceId, 1L);

        repository.upsert(doc(sourceId, "bayat diriltme denemesi", null), TENANT, 1L, MODEL, DIMS, vector(0.1));

        RagDocumentRepository.DocState state =
                repository.findStateBySource(TENANT, FactDocumentFactory.SOURCE_OBSERVATION, sourceId);
        assertThat(state.deleted()).isTrue();
        assertThat(repository.countLive(TENANT)).isZero();
    }

    @Test
    @DisplayName("IT-4: daha yeni version meşru şekilde tombstone'u geri alır")
    void newerVersion_resurrectsTombstonedDocument() {
        UUID sourceId = UUID.randomUUID();
        repository.upsert(doc(sourceId, "eski", null), TENANT, 1L, MODEL, DIMS, vector(0.1));
        repository.tombstone(TENANT, FactDocumentFactory.SOURCE_OBSERVATION, sourceId, 1L);

        repository.upsert(doc(sourceId, "yeni", null), TENANT, 3L, MODEL, DIMS, vector(0.1));

        RagDocumentRepository.DocState state =
                repository.findStateBySource(TENANT, FactDocumentFactory.SOURCE_OBSERVATION, sourceId);
        assertThat(state.deleted()).isFalse();
        assertThat(state.version()).isEqualTo(3L);
    }

    @Test
    @DisplayName("IT-5: sınav silme yalnız türetilen analiz dokümanlarını tombstone'lar; gözlemler etkilenmez")
    void examDeletion_tombstonesOnlyDerivedDocuments() {
        UUID examId = UUID.randomUUID();
        UUID analysis1 = UUID.randomUUID();
        UUID analysis2 = UUID.randomUUID();
        UUID observationSource = UUID.randomUUID();
        repository.upsert(doc(FactDocumentFactory.SOURCE_CLASS_ANALYSIS, analysis1, "ders 1", null, null, examId),
                TENANT, 1L, MODEL, DIMS, vector(0.1));
        repository.upsert(doc(FactDocumentFactory.SOURCE_CLASS_ANALYSIS, analysis2, "ders 2", null, null, examId),
                TENANT, 1L, MODEL, DIMS, vector(0.1));
        repository.upsert(doc(FactDocumentFactory.SOURCE_OBSERVATION, observationSource, "sınava atıf yapan gözlem",
                null, null, examId), TENANT, 1L, MODEL, DIMS, vector(0.1));

        int affected = repository.tombstoneByExam(TENANT, examId, 100L);

        assertThat(affected).isEqualTo(2);
        assertThat(repository.countLive(TENANT)).isEqualTo(1);
        assertThat(repository.findStateBySource(TENANT, FactDocumentFactory.SOURCE_OBSERVATION, observationSource)
                .deleted()).isFalse();
    }

    @Test
    @DisplayName("IT-9: kaynak fact silinmişse guarded upsert dokümanı yeniden oluşturmaz")
    void guardedUpsert_doesNotResurrectDeletedSource() {
        UUID observationId = UUID.randomUUID();
        FactDocumentFactory.FactDocument observationDoc =
                doc(FactDocumentFactory.SOURCE_OBSERVATION, observationId, "gözlem", null, null, null);

        // Kaynak satır yok (silme commit edilmiş) → atomik guard yazımı engeller.
        repository.upsertGuarded(observationDoc, TENANT, 5L, MODEL, DIMS, vector(0.1));
        assertThat(repository.countLive(TENANT)).isZero();

        // Kaynak canlıyken guard engellemez.
        UUID teacherId = UUID.randomUUID();
        UUID classroomId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        insertObservation(teacherId, classroomId, subjectId, observationId, "gözlem", 1L);
        repository.upsertGuarded(observationDoc, TENANT, 5L, MODEL, DIMS, vector(0.1));
        assertThat(repository.countLive(TENANT)).isEqualTo(1);

        // Sınıf analizi: trend/sınav satırı yokken (sınav silinmiş) yazım oluşmaz.
        UUID examId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        FactDocumentFactory.FactDocument analysisDoc = doc(FactDocumentFactory.SOURCE_CLASS_ANALYSIS,
                analysisId, "analiz", classroomId, subjectId, examId);
        repository.upsertGuarded(analysisDoc, TENANT, 7L, MODEL, DIMS, vector(0.1));
        assertThat(repository.findStateBySource(TENANT, FactDocumentFactory.SOURCE_CLASS_ANALYSIS, analysisId))
                .isNull();

        // Trend satırı canlıysa normal yazım gerçekleşir.
        insertTrendSource(classroomId, subjectId, examId);
        repository.upsertGuarded(analysisDoc, TENANT, 7L, MODEL, DIMS, vector(0.1));
        assertThat(repository.findStateBySource(TENANT, FactDocumentFactory.SOURCE_CLASS_ANALYSIS, analysisId))
                .isNotNull();
    }

    // ── Retrieval ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("IT-6: arama tombstone'ları ve izinli olmayan model imzalarını dışlar")
    void search_excludesTombstonesAndDisallowedModels() {
        UUID liveAllowed = UUID.randomUUID();
        UUID liveDisallowed = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();

        repository.upsert(doc(liveAllowed, "izinli", null), TENANT, 1L, "allowed@1536", DIMS, vector(0.1));
        repository.upsert(doc(liveDisallowed, "yasaklı", null), TENANT, 1L, "other@1536", DIMS, vector(0.1));
        repository.upsert(doc(deleted, "silinmiş", null), TENANT, 1L, "allowed@1536", DIMS, vector(0.1));
        repository.tombstone(TENANT, FactDocumentFactory.SOURCE_OBSERVATION, deleted, 1L);

        List<RagDocumentRepository.RagHit> hits = repository.search(
                TENANT, vector(0.1), null, null, 10, List.of("allowed@1536"));

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).sourceId()).isEqualTo(liveAllowed);
        assertThat(hits.get(0).embeddingModel()).isEqualTo("allowed@1536");
    }

    // ── Backfill (model geçişi) ───────────────────────────────────────────────

    @Test
    @DisplayName("IT-7: backfill eski model imzalı dokümanları yeniden embed eder, version'u korur")
    void backfill_reembedsOldModelWithoutChangingVersion() {
        UUID sourceId = UUID.randomUUID();
        repository.upsert(doc(sourceId, "geçiş içeriği", null), TENANT, 5L, "old@1536", DIMS, vector(0.1));

        List<RagDocumentRepository.BackfillCandidate> candidates =
                repository.findBackfillCandidates(TENANT, MODEL, 50);
        assertThat(candidates).hasSize(1);

        repository.updateEmbedding(TENANT, candidates.get(0).sourceType(), candidates.get(0).sourceId(),
                vector(0.2), MODEL, DIMS);

        assertThat(repository.findBackfillCandidates(TENANT, MODEL, 50)).isEmpty();
        RagDocumentRepository.DocState state =
                repository.findStateBySource(TENANT, FactDocumentFactory.SOURCE_OBSERVATION, sourceId);
        assertThat(state.version()).isEqualTo(5L);
        assertThat(state.embeddingModel()).isEqualTo(MODEL);
    }

    // ── Ingestion (hash-skip + tombstone) ────────────────────────────────────

    @Test
    @DisplayName("IT-8: ingestion aynı içerik+modelde embed etmez; silme sonrası bayat event yok sayılır")
    void ingestion_skipsUnchangedContent_andIgnoresStaleAfterDelete() {
        UUID teacherId = UUID.randomUUID();
        UUID classroomId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID observationId = UUID.randomUUID();
        insertObservation(teacherId, classroomId, subjectId, observationId, "ilk gözlem", 1L);

        ingestionService.ingestObservation(TENANT, observationId, 1L);
        verify(embeddingModel, times(1)).embed(anyString());
        assertThat(repository.countLive(TENANT)).isEqualTo(1);

        // Aynı içerik + aynı model → yeniden embed yok
        ingestionService.ingestObservation(TENANT, observationId, 1L);
        verify(embeddingModel, times(1)).embed(anyString());

        // İçerik değişimi → yeni embed
        jdbcTemplate.update("UPDATE " + TENANT + ".teacher_observations SET content = ?, version = 2 WHERE id = ?",
                "güncellenmiş gözlem", observationId);
        ingestionService.ingestObservation(TENANT, observationId, 2L);
        verify(embeddingModel, times(2)).embed(anyString());

        // Silme + bayat update → dirilme yok, ekstra embed yok
        ingestionService.deleteObservation(TENANT, observationId, 3L);
        ingestionService.ingestObservation(TENANT, observationId, 2L);
        verify(embeddingModel, times(2)).embed(anyString());

        RagDocumentRepository.DocState state =
                repository.findStateBySource(TENANT, FactDocumentFactory.SOURCE_OBSERVATION, observationId);
        assertThat(state.deleted()).isTrue();
        assertThat(repository.countLive(TENANT)).isZero();
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private void insertObservation(UUID teacherId, UUID classroomId, UUID subjectId,
                                   UUID observationId, String content, long version) {
        jdbcTemplate.update("""
                INSERT INTO %1$s.users (id, username, password_hash, email, role)
                VALUES (?, ?, 'x', ?, 'TEACHER') ON CONFLICT DO NOTHING
                """.formatted(TENANT), teacherId, "it_" + teacherId, teacherId + "@it.test");
        jdbcTemplate.update("INSERT INTO %s.classrooms (id, name) VALUES (?, 'IT Sınıfı')"
                .formatted(TENANT), classroomId);
        jdbcTemplate.update("""
                INSERT INTO %s.subjects (id, name) VALUES (?, ?) ON CONFLICT DO NOTHING
                """.formatted(TENANT), subjectId, "IT Ders " + subjectId);
        jdbcTemplate.update("""
                INSERT INTO %1$s.teacher_observations
                    (id, teacher_id, classroom_id, subject_id, content, version)
                VALUES (?, ?, ?, ?, ?, ?)
                """.formatted(TENANT), observationId, teacherId, classroomId, subjectId, content, version);
    }

    private void insertTrendSource(UUID classroomId, UUID subjectId, UUID examId) {
        jdbcTemplate.update("""
                INSERT INTO %s.classrooms (id, name) VALUES (?, 'IT Sınıfı')
                ON CONFLICT DO NOTHING
                """.formatted(TENANT), classroomId);
        jdbcTemplate.update("""
                INSERT INTO %s.subjects (id, name) VALUES (?, ?)
                ON CONFLICT DO NOTHING
                """.formatted(TENANT), subjectId, "IT Ders " + subjectId);
        jdbcTemplate.update("""
                INSERT INTO %s.exams (id, title, exam_date, exam_type)
                VALUES (?, 'IT Sınavı', CURRENT_DATE, 'TYT')
                """.formatted(TENANT), examId);
        jdbcTemplate.update("""
                INSERT INTO %1$s.class_subject_trends
                    (classroom_id, subject_id, exam_id, avg_net, avg_score, student_count, trend_direction)
                VALUES (?, ?, ?, 50.00, 300.00, 10, 'STABLE')
                """.formatted(TENANT), classroomId, subjectId, examId);
    }

    private FactDocumentFactory.FactDocument doc(UUID sourceId, String content, UUID examId) {
        return doc(FactDocumentFactory.SOURCE_OBSERVATION, sourceId, content, null, null, examId);
    }

    private FactDocumentFactory.FactDocument doc(String sourceType, UUID sourceId, String content,
                                                 UUID classroomId, UUID subjectId, UUID examId) {
        return new FactDocumentFactory.FactDocument(
                sourceType, sourceId, classroomId, subjectId, examId,
                content, Map.of("classroomName", "IT Sınıfı"));
    }

    private static List<Double> vector(double value) {
        return IntStream.range(0, DIMS).mapToObj(i -> value).toList();
    }
}
