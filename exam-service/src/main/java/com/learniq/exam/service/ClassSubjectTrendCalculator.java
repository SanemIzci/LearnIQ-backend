package com.learniq.exam.service;

import com.learniq.exam.messaging.KafkaAfterCommit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * EXAM_FINALIZED event'i sonrasında her (sınıf × ders) kombinasyonu için
 * class_subject_trends tablosunu dolduran/güncelleyen motor.
 *
 * Algoritma:
 * 1. Sınava ait sınıfları bul (classroom_students üzerinden).
 * 2. Her sınıf + ders için avg_net, avg_score, student_count hesapla.
 * 3. Bir önceki sınavın avg_net'ini tarih sırasına göre bul → delta hesapla.
 * 4. ON CONFLICT (classroom_id, subject_id, exam_id) DO UPDATE ile upsert yap.
 * 5. class-analysis-topic'e CLASS_ANALYSIS_COMPLETED event'i yayımla.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassSubjectTrendCalculator {

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final BigDecimal TREND_THRESHOLD = new BigDecimal("0.5");

    @Transactional
    public void calculate(String tenantId, UUID examId) {
        String schema = com.learniq.common.tenant.TenantSchemaValidator.requireValid(tenantId);
        log.info("Trend hesaplama başladı: tenantId={}, examId={}", tenantId, examId);

        // 1. Sınava katılan öğrencilerin bulunduğu sınıfları bul
        List<UUID> classroomIds = jdbcTemplate.queryForList(
                """
                SELECT DISTINCT cs.classroom_id
                FROM %1$s.classroom_students cs
                JOIN %1$s.student_exam_results ser ON ser.student_id = cs.student_id
                WHERE ser.exam_id = ? AND ser.status = 'APPROVED'
                """.formatted(schema),
                UUID.class, examId);

        if (classroomIds.isEmpty()) {
            log.info("Sınav {} için onaylı sonuç bulunamadı; trend hesaplanmıyor.", examId);
            return;
        }

        // 2. Her sınıf için ders bazlı istatistikleri hesapla (aggregate version üret)
        Map<UUID, Long> calcVersions = new LinkedHashMap<>();
        for (UUID classroomId : classroomIds) {
            calcVersions.put(classroomId, calculateForClassroom(schema, examId, classroomId));
        }

        // 3. CLASS_ANALYSIS_COMPLETED event yayımla (her sınıf için, aynı aggregateVersion)
        for (Map.Entry<UUID, Long> entry : calcVersions.entrySet()) {
            publishAnalysisCompleted(tenantId, examId, entry.getKey(), entry.getValue());
        }

        log.info("Trend hesaplama tamamlandı: examId={}, {} sınıf işlendi.", examId, classroomIds.size());
    }

    /**
     * @return bu (classroom, exam) aggregate'i için monotonik hesaplama version'u.
     *         Aynı hesaplama TX'inde yazılan tüm fact satırları bu version'u paylaşır.
     */
    private long calculateForClassroom(String schema, UUID examId, UUID classroomId) {
        long calcVersion = nextCalcVersion(schema, classroomId, examId);
        // Sınıftaki toplam kayıtlı öğrenci sayısı (expected_student_count)
        Integer expectedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM %s.classroom_students WHERE classroom_id = ?".formatted(schema),
                Integer.class, classroomId);
        int expectedStudentCount = expectedCount != null ? expectedCount : 0;

        // Ders bazlı istatistikler
        List<Map<String, Object>> subjectStats = jdbcTemplate.queryForList(
                """
                SELECT ssr.subject_id,
                       AVG(ssr.net_score)    AS avg_net,
                       AVG(ser.total_score)  AS avg_score,
                       COUNT(DISTINCT ssr.student_id) AS student_count
                FROM %1$s.student_subject_results ssr
                JOIN %1$s.student_exam_results ser
                  ON ser.student_id = ssr.student_id AND ser.exam_id = ssr.exam_id
                JOIN %1$s.classroom_students cs ON cs.student_id = ssr.student_id
                WHERE ssr.exam_id = ?
                  AND cs.classroom_id = ?
                  AND ser.status = 'APPROVED'
                GROUP BY ssr.subject_id
                """.formatted(schema),
                examId, classroomId);

        for (Map<String, Object> row : subjectStats) {
            UUID subjectId = toUuid(row.get("subject_id"));
            BigDecimal avgNet = toBigDecimal(row.get("avg_net"));
            BigDecimal avgScore = toBigDecimal(row.get("avg_score"));
            int studentCount = toInt(row.get("student_count"));

            BigDecimal participationRate = expectedStudentCount > 0
                    ? BigDecimal.valueOf(studentCount * 100.0 / expectedStudentCount)
                            .setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            // Bir önceki sınavın avg_net'i
            BigDecimal previousAvgNet = getPreviousAvgNet(schema, classroomId, subjectId, examId);
            BigDecimal delta = previousAvgNet != null
                    ? avgNet.subtract(previousAvgNet).setScale(2, RoundingMode.HALF_UP)
                    : null;

            String trendDirection = computeTrend(delta);

            upsertTrend(schema, examId, classroomId, subjectId, avgNet, avgScore, studentCount,
                    expectedStudentCount, participationRate, delta, trendDirection, calcVersion);
        }
        return calcVersion;
    }

    private long nextCalcVersion(String schema, UUID classroomId, UUID examId) {
        Long current = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(calc_version), 0) FROM %s.class_subject_trends WHERE classroom_id = ? AND exam_id = ?"
                        .formatted(schema),
                Long.class, classroomId, examId);
        return (current == null ? 0L : current) + 1L;
    }

    /**
     * Aynı (classroom, subject) çifti için, exam_date bakımından mevcut sınavdan önceki
     * en son sınavın avg_net değerini döndürür.
     */
    private BigDecimal getPreviousAvgNet(String schema, UUID classroomId, UUID subjectId, UUID currentExamId) {
        List<BigDecimal> result = jdbcTemplate.queryForList(
                """
                SELECT cst.avg_net
                FROM %1$s.class_subject_trends cst
                JOIN %1$s.exams e ON e.id = cst.exam_id
                WHERE cst.classroom_id = ?
                  AND cst.subject_id   = ?
                  AND cst.exam_id      != ?
                  AND e.exam_date < (SELECT exam_date FROM %1$s.exams WHERE id = ?)
                ORDER BY e.exam_date DESC, e.created_at DESC
                LIMIT 1
                """.formatted(schema),
                BigDecimal.class, classroomId, subjectId, currentExamId, currentExamId);

        return result.isEmpty() ? null : result.get(0);
    }

    private void upsertTrend(String schema, UUID examId, UUID classroomId, UUID subjectId,
                              BigDecimal avgNet, BigDecimal avgScore, int studentCount,
                              int expectedStudentCount, BigDecimal participationRate,
                              BigDecimal delta, String trendDirection, long calcVersion) {
        jdbcTemplate.update(
                """
                INSERT INTO %s.class_subject_trends
                    (classroom_id, subject_id, exam_id,
                     avg_net, avg_score, student_count,
                     expected_student_count, participation_rate,
                     delta_from_previous, trend_direction, calc_version, calculated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (classroom_id, subject_id, exam_id) DO UPDATE SET
                    avg_net               = EXCLUDED.avg_net,
                    avg_score             = EXCLUDED.avg_score,
                    student_count         = EXCLUDED.student_count,
                    expected_student_count= EXCLUDED.expected_student_count,
                    participation_rate    = EXCLUDED.participation_rate,
                    delta_from_previous   = EXCLUDED.delta_from_previous,
                    trend_direction       = EXCLUDED.trend_direction,
                    calc_version          = EXCLUDED.calc_version,
                    calculated_at         = NOW()
                """.formatted(schema),
                classroomId, subjectId, examId,
                avgNet, avgScore, studentCount,
                expectedStudentCount, participationRate,
                delta, trendDirection, calcVersion);
    }

    private String computeTrend(BigDecimal delta) {
        if (delta == null) return "STABLE";
        int cmp = delta.compareTo(TREND_THRESHOLD);
        if (cmp > 0) return "UP";
        if (delta.compareTo(TREND_THRESHOLD.negate()) < 0) return "DOWN";
        return "STABLE";
    }

    private void publishAnalysisCompleted(String tenantId, UUID examId, UUID classroomId, long calcVersion) {
        com.learniq.common.tenant.TenantSchemaValidator.requireValid(tenantId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("examId", examId.toString());
        payload.put("classroomId", classroomId.toString());

        Map<String, Object> event = com.learniq.common.messaging.RagEvent.envelope(
                "CLASS_ANALYSIS_COMPLETED", tenantId, aggregateId(classroomId, examId), calcVersion, payload);

        // Partition key = aggregateId (classroom × exam) → aynı analiz eventleri sıralı.
        KafkaAfterCommit.send(kafkaTemplate, "class-analysis-topic",
                aggregateId(classroomId, examId), event);
        log.info("CLASS_ANALYSIS_COMPLETED kuyruğa alındı: examId={}, classroomId={}, version={}",
                examId, classroomId, calcVersion);
    }

    private String aggregateId(UUID classroomId, UUID examId) {
        return classroomId + ":" + examId;
    }

    // ── Type-safe helpers ────────────────────────────────────────────────────

    private UUID toUuid(Object val) {
        if (val == null) return null;
        return val instanceof UUID u ? u : UUID.fromString(val.toString());
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd.setScale(2, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(((Number) val).doubleValue()).setScale(2, RoundingMode.HALF_UP);
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        return ((Number) val).intValue();
    }
}
