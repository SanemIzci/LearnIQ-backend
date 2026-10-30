package com.learniq.ai.rag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * RAG doküman fabrikası — saf mantık, DB bağımlılığı yok.
 *
 * Kural: Sayısal değerler (net, delta, katılım) kaynaktan ALINDIĞI GİBİ metne gömülür;
 * yorum/tahmin üretilmez. Embedding'e giden metin ile content_hash aynı normalize edilmiş
 * içerikten üretilir → deterministic hash idempotency.
 */
public final class FactDocumentFactory {

    public static final String SOURCE_CLASS_ANALYSIS = "CLASS_ANALYSIS_FACT";
    public static final String SOURCE_OBSERVATION = "TEACHER_OBSERVATION";

    private FactDocumentFactory() {}

    // ── Normalize + Hash ─────────────────────────────────────────────────────

    /** Embedding öncesi içerik normalizasyonu: trim + whitespace düzleştirme. */
    public static String normalize(String content) {
        if (content == null) return "";
        return content.strip().replaceAll("\\s+", " ");
    }

    /** SHA-256 hex — normalize edilmiş içerik üzerinden. */
    public static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalize(content).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 MessageDigest bulunamadı", e);
        }
    }

    /** Deterministik UUID: birden fazla doğal anahtarın karışımından üretilir (rag_documents.source_id için). */
    public static UUID deterministicUuid(Object... parts) {
        String joined = String.join(":", java.util.Arrays.stream(parts).map(String::valueOf).toList());
        return UUID.nameUUIDFromBytes(joined.getBytes(StandardCharsets.UTF_8));
    }

    // ── Class Analysis Fact Dokümanı ────────────────────────────────────────

    /**
     * Sınıf-ders deneme analizinden tek kanonik fact dokümanı üretir.
     *
     * @param row sınıf-ders trend satırı (avg_net, delta_from_previous, trend_direction, ...)
     */
    public static FactDocument classAnalysisFact(ClassAnalysisFactRow row) {
        StringBuilder content = new StringBuilder();
        content.append("Sınıf analiz edildi: ")
                .append(row.classroomName()).append(" — ")
                .append(row.subjectName())
                .append(" dersi, ").append(row.examTitle()).append(" denemesi (")
                .append(formatDate(row.examDate())).append(").\n");
        content.append("Sınıf ortalama net: ").append(formatValue(row.avgNet())).append(".\n");

        if (row.deltaFromPrevious() != null && row.previousExamTitle() != null && !row.previousExamTitle().isBlank()) {
            content.append("Bir önceki denemeye (").append(row.previousExamTitle())
                    .append(") göre değişim: ").append(signed(row.deltaFromPrevious()))
                    .append(" (trend: ").append(row.trendDirection()).append(").\n");
        } else if (row.previousExamTitle() == null) {
            content.append("Bu ders için önceki deneme verisi yok; trend referansı: STABLE.\n");
        }

        content.append("Katılım: ").append(row.studentCount()).append("/").append(row.expectedStudentCount())
                .append(" öğrenci");
        if (row.participationRate() != null) {
            content.append(" (").append(formatValue(row.participationRate())).append("%)");
        }
        content.append(".\n");

        if (row.avgScore() != null) {
            content.append("Ortalama puan: ").append(formatValue(row.avgScore())).append(".\n");
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("classroomId", row.classroomId().toString());
        metadata.put("subjectId", row.subjectId().toString());
        metadata.put("examId", row.examId().toString());
        metadata.put("classroomName", row.classroomName());
        metadata.put("subjectName", row.subjectName());
        metadata.put("examTitle", row.examTitle());
        metadata.put("avgNet", row.avgNet());
        metadata.put("avgScore", row.avgScore());
        metadata.put("deltaFromPrevious", row.deltaFromPrevious());
        metadata.put("trendDirection", row.trendDirection());
        metadata.put("studentCount", row.studentCount());
        metadata.put("expectedStudentCount", row.expectedStudentCount());
        metadata.put("participationRate", row.participationRate());
        metadata.put("tenantId", row.tenantId());
        metadata.put("calculatedAt", row.calculatedAt() != null ? row.calculatedAt().toString() : null);

        return new FactDocument(
                SOURCE_CLASS_ANALYSIS,
                deterministicUuid(row.classroomId(), row.subjectId(), row.examId()),
                row.classroomId(),
                row.subjectId(),
                normalize(content.toString()),
                metadata);
    }

    // ── Teacher Observation Dokümanı ────────────────────────────────────────

    /**
     * Öğretmen gözlem notundan semantik doküman üretir.
     * KVKK: embedded metinde öğrenci adı yerine sınıf/ders/deneme bağlamı kullanılır;
     * öğretmen kimliği yalnızca opak UUID olarak metadata'ya konur.
     */
    public static FactDocument observationDocument(ObservationRow row) {
        StringBuilder content = new StringBuilder();
        content.append("Öğretmen gözlem notu (")
                .append(row.classroomName() != null ? row.classroomName() : "sınıf bilinmiyor")
                .append(row.subjectName() != null ? " — " + row.subjectName() : "")
                .append(").\n");
        content.append(row.content());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("classroomId", row.classroomId() != null ? row.classroomId().toString() : null);
        metadata.put("subjectId", row.subjectId() != null ? row.subjectId().toString() : null);
        metadata.put("examId", row.examId() != null ? row.examId().toString() : null);
        metadata.put("classroomName", row.classroomName());
        metadata.put("subjectName", row.subjectName());
        metadata.put("teacherRef", row.teacherId() != null ? row.teacherId().toString() : null);
        metadata.put("tags", row.tags() != null ? row.tags() : "[]");
        metadata.put("observedAt", row.createdAt() != null ? row.createdAt().toString() : null);
        metadata.put("tenantId", row.tenantId());

        return new FactDocument(
                SOURCE_OBSERVATION,
                row.observationId(),
                row.classroomId(),
                row.subjectId(),
                normalize(content.toString()),
                metadata);
    }

    // ── Value Objects ───────────────────────────────────────────────────────

    /** Uretilen RAG dokümanı: source_type + source_id unique; content hash'lenir, metadata JSONB'ye. */
    public record FactDocument(
            String sourceType,
            UUID sourceId,
            UUID classroomId,
            UUID subjectId,
            String content,
            Map<String, Object> metadata) {}

    /** class_subject_trends + isim zenginleştirmesi içeren fact satırı. */
    public record ClassAnalysisFactRow(
            String tenantId,
            UUID classroomId,
            UUID subjectId,
            UUID examId,
            String classroomName,
            String subjectName,
            String examTitle,
            LocalDate examDate,
            String previousExamTitle,
            java.math.BigDecimal avgNet,
            java.math.BigDecimal avgScore,
            int studentCount,
            int expectedStudentCount,
            java.math.BigDecimal participationRate,
            java.math.BigDecimal deltaFromPrevious,
            String trendDirection,
            LocalDateTime calculatedAt) {}

    /** teacher_observations + isim zenginleştirmesi içeren gözlem satırı. */
    public record ObservationRow(
            String tenantId,
            UUID observationId,
            UUID teacherId,
            UUID classroomId,
            UUID subjectId,
            UUID examId,
            String classroomName,
            String subjectName,
            String content,
            String tags,
            LocalDateTime createdAt) {}

    // ── Formatting helpers ──────────────────────────────────────────────────

    /** BigDecimal/Double → nokta ondalık ayraçlı tutarlı temsil (LLM'e ham değer olarak gider). */
    private static String formatValue(Number value) {
        if (value == null) return "0";
        if (value instanceof java.math.BigDecimal bd) {
            return bd.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }

    private static String signed(Number value) {
        if (value == null) return "0";
        double d = value.doubleValue();
        String plain = formatValue(value);
        return d > 0 ? "+" + plain : plain;
    }

    private static String formatDate(LocalDate date) {
        return date != null ? date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : "tarih bilinmiyor";
    }
}
