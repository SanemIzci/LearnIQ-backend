package com.learniq.ai.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FactDocumentFactory Unit Tests")
class FactDocumentFactoryTest {

    private final String tenant = "akademi_1";
    private final UUID classroomId = UUID.randomUUID();
    private final UUID subjectId = UUID.randomUUID();
    private final UUID examId = UUID.randomUUID();
    private final UUID observationId = UUID.randomUUID();

    // ── normalize + hash ─────────────────────────────────────────────────────

    @Test
    @DisplayName("normalize - trim + whitespace düzleştirme")
    void normalize_collapsesWhitespace() {
        assertThat(FactDocumentFactory.normalize("  a\n\nb\t c  ")).isEqualTo("a b c");
        assertThat(FactDocumentFactory.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("sha256Hex - aynı içerik aynı hash, farklı içerik farklı hash")
    void sha256_deterministic() {
        String h1 = FactDocumentFactory.sha256Hex("Türev konusu zorlandı.");
        String h2 = FactDocumentFactory.sha256Hex("Türev   konusu\nzorlandı. ");
        String h3 = FactDocumentFactory.sha256Hex("Farklı içerik.");

        assertThat(h1).isEqualTo(h2);
        assertThat(h1).hasSize(64);
        assertThat(h1).isNotEqualTo(h3);
    }

    @Test
    @DisplayName("deterministicUuid - aynı parçalar aynı UUID")
    void deterministicUuid_stable() {
        UUID u1 = FactDocumentFactory.deterministicUuid(classroomId, subjectId, examId);
        UUID u2 = FactDocumentFactory.deterministicUuid(classroomId, subjectId, examId);
        assertThat(u1).isEqualTo(u2);
    }

    // ── class analysis fact ──────────────────────────────────────────────────

    @Test
    @DisplayName("classAnalysisFact - sayısal değerler ham olarak metne gömülür, yorum üretilmez")
    void classAnalysisFact_embedsRawNumbers() {
        FactDocumentFactory.FactDocument doc = FactDocumentFactory.classAnalysisFact(
                new FactDocumentFactory.ClassAnalysisFactRow(
                        tenant, classroomId, subjectId, examId,
                        "12-A", "Matematik", "TYT Deneme 3",
                        LocalDate.of(2026, 10, 20),
                        "TYT Deneme 2",
                        new BigDecimal("24.60"),
                        new BigDecimal("312.50"),
                        28, 30,
                        new BigDecimal("93.33"),
                        new BigDecimal("1.40"),
                        "UP",
                        LocalDateTime.of(2026, 10, 27, 18, 0)));

        assertThat(doc.sourceType()).isEqualTo(FactDocumentFactory.SOURCE_CLASS_ANALYSIS);
        // unique source: (classroom, subject, exam) -> deterministic UUID
        assertThat(doc.sourceId()).isEqualTo(FactDocumentFactory.deterministicUuid(classroomId, subjectId, examId));
        assertThat(doc.classroomId()).isEqualTo(classroomId);
        assertThat(doc.subjectId()).isEqualTo(subjectId);

        String content = doc.content();
        assertThat(content)
                .contains("12-A")
                .contains("Matematik")
                .contains("TYT Deneme 3")
                .contains("24.6")            // ham avg_net (trailing zero kırpılmış)
                .contains("+1.4")            // ham delta (işaretle)
                .contains("UP")
                .contains("28/30")
                .contains("93.33");

        // sayısal iddia yalnızca source fact'ten gelir; yorum yok
        assertThat(content).doesNotContain("tahmin");

        Map<String, Object> metadata = doc.metadata();
        assertThat(metadata)
                .containsEntry("tenantId", tenant)
                .containsEntry("trendDirection", "UP")
                .containsEntry("avgNet", new BigDecimal("24.60"))
                .containsEntry("deltaFromPrevious", new BigDecimal("1.40"))
                .containsEntry("studentCount", 28)
                .containsEntry("expectedStudentCount", 30);
    }

    @Test
    @DisplayName("classAnalysisFact - önceki deneme yoksa STABLE referansı belirtilir")
    void classAnalysisFact_noPrevious_stableReference() {
        FactDocumentFactory.FactDocument doc = FactDocumentFactory.classAnalysisFact(
                new FactDocumentFactory.ClassAnalysisFactRow(
                        tenant, classroomId, subjectId, examId,
                        "10-B", "Fizik", "Deneme 1", LocalDate.of(2026, 9, 1),
                        null,
                        new BigDecimal("18.00"), null, 20, 22,
                        new BigDecimal("90.91"), null, "STABLE", null));

        assertThat(doc.content()).contains("önceki deneme verisi yok");
        assertThat(doc.metadata()).containsEntry("trendDirection", "STABLE");
    }

    // ── observation document ────────────────────────────────────────────────

    @Test
    @DisplayName("observationDocument - içerik korunur, öğretmen kimliği metadata'da opak UUID")
    void observationDocument_buildsContentAndMetadata() {
        FactDocumentFactory.FactDocument doc = FactDocumentFactory.observationDocument(
                new FactDocumentFactory.ObservationRow(
                        tenant, observationId, UUID.randomUUID(), classroomId, subjectId, null,
                        "12-A", "Matematik",
                        "Türev konusunda sınıfın tamamı fire yaptı; temel limit konusunu tekrar çalıştıracağız.",
                        "[\"geometri_eksigi\",\"dikkat\"]",
                        LocalDateTime.of(2026, 10, 26, 9, 30)));

        assertThat(doc.sourceType()).isEqualTo(FactDocumentFactory.SOURCE_OBSERVATION);
        assertThat(doc.sourceId()).isEqualTo(observationId);
        assertThat(doc.content()).contains("Türev konusunda").contains("12-A");
        Map<String, Object> metadata = doc.metadata();
        assertThat(metadata)
                .containsEntry("classroomName", "12-A")
                .containsEntry("tags", "[\"geometri_eksigi\",\"dikkat\"]")
                // opak referans: gerçek ad yok
                .hasEntrySatisfying("teacherRef", v -> assertThat((String) v).hasSize(36));
    }
}
