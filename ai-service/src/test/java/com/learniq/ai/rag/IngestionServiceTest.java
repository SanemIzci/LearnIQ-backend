package com.learniq.ai.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IngestionService Unit Tests")
class IngestionServiceTest {

    @Mock private TenantSchemaService tenantSchemaService;
    @Mock private RagDocumentRepository ragDocumentRepository;
    @Mock private EmbeddingModel embeddingModel;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private RagMetrics ragMetrics;

    @InjectMocks private IngestionService ingestionService;

    private final String tenant = "akademi_1";
    private UUID examId;
    private UUID classroomId;
    private UUID subjectId;

    @BeforeEach
    void setUp() {
        examId = UUID.randomUUID();
        classroomId = UUID.randomUUID();
        subjectId = UUID.randomUUID();
        ReflectionTestUtils.setField(ingestionService, "embeddingModelSig", "text-embedding-3-small@1536");
        ReflectionTestUtils.setField(ingestionService, "embeddingDims", 1536);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> trendRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("subject_id", subjectId.toString());
        row.put("exam_id", examId.toString());
        row.put("subject_name", "Matematik");
        row.put("exam_title", "TYT Deneme 3");
        row.put("exam_date", java.sql.Date.valueOf("2026-10-20"));
        row.put("previous_exam_title", "TYT Deneme 2");
        row.put("avg_net", new BigDecimal("24.60"));
        row.put("avg_score", new BigDecimal("312.00"));
        row.put("student_count", 28L);
        row.put("participation_rate", new BigDecimal("93.33"));
        row.put("delta_from_previous", new BigDecimal("1.40"));
        row.put("trend_direction", "UP");
        row.put("calculated_at", java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 10, 27, 18, 0)));
        return row;
    }

    private void stubFactReads() {
        doReturn(List.of(trendRow())).when(jdbcTemplate)
                .queryForList(contains("class_subject_trends"), eq(classroomId), eq(examId));
        when(jdbcTemplate.queryForObject(contains(tenant + ".classrooms"), eq(String.class), eq(classroomId)))
                .thenReturn("12-A");
        when(jdbcTemplate.queryForObject(contains(tenant + ".classroom_students"), eq(Integer.class), eq(classroomId)))
                .thenReturn(30);
    }

    // ── class analysis ingestion ─────────────────────────────────────────────

    @Test
    @DisplayName("ingestClassAnalysis - şemaya geçilir, fact dokümana dönüştürülür, embedding upsert edilir")
    void ingestClassAnalysis_embedsAndUpserts() {
        stubFactReads();
        when(embeddingModel.embed(anyString())).thenReturn(List.of(0.1, 0.2));
        when(ragDocumentRepository.findStateBySource(anyString(), anyString(), any()))
                .thenReturn(null); // yeni doküman

        ingestionService.ingestClassAnalysis(tenant, examId, classroomId);

        verify(tenantSchemaService).switchTo(tenant);
        verify(embeddingModel).embed(anyString());

        ArgumentCaptor<FactDocumentFactory.FactDocument> docCaptor =
                ArgumentCaptor.forClass(FactDocumentFactory.FactDocument.class);
        verify(ragDocumentRepository).upsertGuarded(docCaptor.capture(), eq(tenant), eq(0L),
                eq("text-embedding-3-small@1536"), eq(1536), eq(List.of(0.1, 0.2)));
        assertThat(docCaptor.getValue().content()).contains("12-A").contains("24.6").contains("+1.4");
    }

    @Test
    @DisplayName("ingestClassAnalysis - tablolar doğrulanmış şema adıyla niteliklenir (search_path'e güvenilmez)")
    void ingestClassAnalysis_schemaQualifiedSql() {
        stubFactReads();
        when(embeddingModel.embed(anyString())).thenReturn(List.of(0.1));
        when(ragDocumentRepository.findStateBySource(anyString(), anyString(), any())).thenReturn(null);

        ingestionService.ingestClassAnalysis(tenant, examId, classroomId);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), eq(classroomId), eq(examId));
        assertThat(sqlCaptor.getValue())
                .contains(tenant + ".class_subject_trends")
                .contains(tenant + ".exams")
                .contains(tenant + ".subjects");
    }

    @Test
    @DisplayName("ingestClassAnalysis - üretilecek içerik hash'i depodakine eşitse embedding çağrılmaz")
    void ingestClassAnalysis_sameHash_skipsEmbedding() {
        stubFactReads();
        when(ragDocumentRepository.findStateBySource(anyString(), anyString(), any())).thenAnswer(inv ->
                // gerçek akışla birebir: aynı factory üretimi deterministic olduğu için
                // servisin üreteceği içerikten hash üretmek geçerli stub'dır
                new RagDocumentRepository.DocState(
                        FactDocumentFactory.sha256Hex(expectedAnalysisContent()),
                        0L, false, "text-embedding-3-small@1536"));

        ingestionService.ingestClassAnalysis(tenant, examId, classroomId);

        verify(embeddingModel, never()).embed(anyString());
        verify(ragDocumentRepository, never()).upsertGuarded(any(), anyString(), anyLong(), anyString(), anyInt(), any());
    }

    @Test
    @DisplayName("ingestClassAnalysis - trend satırı yoksa hiçbir şey yapılmaz")
    void ingestClassAnalysis_noTrendRows_skips() {
        doReturn(List.of()).when(jdbcTemplate)
                .queryForList(contains("class_subject_trends"), eq(classroomId), eq(examId));

        ingestionService.ingestClassAnalysis(tenant, examId, classroomId);

        verify(embeddingModel, never()).embed(anyString());
        verify(ragDocumentRepository, never()).upsertGuarded(any(), anyString(), anyLong(), anyString(), anyInt(), any());
    }

    // ── observation ingestion ────────────────────────────────────────────────

    @Test
    @DisplayName("ingestObservation - gözlem metni embed edilerek upsert edilir")
    void ingestObservation_embeds() {
        Map<String, Object> obsRow = new HashMap<>();
        obsRow.put("id", UUID.randomUUID().toString());
        obsRow.put("teacher_id", UUID.randomUUID().toString());
        obsRow.put("classroom_id", classroomId.toString());
        obsRow.put("subject_id", subjectId.toString());
        obsRow.put("exam_id", null);
        obsRow.put("content", "Türev konusunda sorun var; konu tekrarı planlandı.");
        obsRow.put("tags", "[\"geometri_eksigi\"]");
        obsRow.put("created_at", java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 10, 26, 9, 30)));
        obsRow.put("classroom_name", "12-A");
        obsRow.put("subject_name", "Matematik");
        UUID observationId = UUID.randomUUID();
        doReturn(obsRow).when(jdbcTemplate).queryForMap(contains(tenant + ".teacher_observations"), eq(observationId));
        when(embeddingModel.embed(anyString())).thenReturn(List.of(0.5, 0.6));
        when(ragDocumentRepository.findStateBySource(anyString(), anyString(), any())).thenReturn(null);

        ingestionService.ingestObservation(tenant, observationId);

        verify(embeddingModel).embed(contains("Türev konusunda"));
        verify(ragDocumentRepository)
                .upsertGuarded(any(FactDocumentFactory.FactDocument.class), eq(tenant), eq(0L),
                        eq("text-embedding-3-small@1536"), eq(1536), eq(List.of(0.5, 0.6)));
    }

    @Test
    @DisplayName("deleteObservation - doküman tombstone'lanır (fiziksel silme yok)")
    void deleteObservation_removesDoc() {
        UUID observationId = UUID.randomUUID();
        when(ragDocumentRepository.tombstone(eq(tenant), eq(FactDocumentFactory.SOURCE_OBSERVATION),
                eq(observationId), eq(0L))).thenReturn(1);

        ingestionService.deleteObservation(tenant, observationId);

        verify(tenantSchemaService).switchTo(tenant);
        verify(ragDocumentRepository).tombstone(eq(tenant), eq(FactDocumentFactory.SOURCE_OBSERVATION),
                eq(observationId), eq(0L));
    }

    /**
     * İngestionService'in trendRow()'dan üreteceği içerikle birebir aynı factory kurulumu.
     * (stub değerleri stubFactReads ile senkron tutulmalı)
     */
    private String expectedAnalysisContent() {
        return FactDocumentFactory.classAnalysisFact(
                new FactDocumentFactory.ClassAnalysisFactRow(
                        tenant, classroomId, subjectId, examId,
                        "12-A", "Matematik", "TYT Deneme 3",
                        LocalDate.of(2026, 10, 20),
                        "TYT Deneme 2",
                        new BigDecimal("24.60"), new BigDecimal("312.00"),
                        28, 30, new BigDecimal("93.33"),
                        new BigDecimal("1.40"), "UP",
                        LocalDateTime.of(2026, 10, 27, 18, 0))).content();
    }
}
