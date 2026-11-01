package com.learniq.ai.chat;

import com.learniq.ai.rag.TenantSchemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Admin RAG chat FunctionCallback araçlarının gerçek iş mantığı.
 *
 * KURAL: Sayısal fact'ler embedding/LLM ile DEĞİL, doğrudan tenant şemasındaki
 * tablolardan okunur (class_subject_trends, student_subject_results ...). Böylece
 * değerler her zaman doğru ve günceldir; LLM yalnızca dili düzenler.
 *
 * SQL güvenliği: her sorgunun başında switchTo() ile TenantSchemaValidator şema
 * adını doğrular; tablo adları da doğrulanmış şema adıyla NİTELİKLİ yazılır
 * (SET search_path havuz bağlantı yapısında bağlantı değişince etkisini
 * yitirebildiği için, nitelikli ad tek doğru yöntemdir).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminChatToolService {

    private final TenantSchemaService tenantSchemaService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${rag.chat.at-risk-default-net-threshold:15.0}")
    private double defaultAtRiskNetThreshold;

    // ── Araç girdi kayıtları (LLM input schema'sı Jackson'dan üretilir) ──────

    public record TrendFilter(UUID classroomId, UUID examId, UUID subjectId) {}

    public record AtRiskFilter(UUID classroomId, UUID examId, Double netThreshold) {}

    public record ComparisonFilter(List<UUID> classroomIds, UUID examId) {}

    public record TenantOverviewFilter() {}

    // ── Çıktı kayıtları (LLM'e JSON olarak gider) ─────────────────────────────

    public record ClassroomSubjectTrend(
            UUID classroomId,
            UUID examId,
            UUID subjectId,
            String classroomName,
            String subjectName,
            String examTitle,
            String examDate,
            BigDecimal avgNet,
            BigDecimal avgScore,
            int studentCount,
            BigDecimal participationRate,
            BigDecimal deltaFromPrevious,
            String trendDirection) {}

    public record AtRiskStudent(
            UUID studentId,
            String fullName,
            BigDecimal avgNet,
            BigDecimal totalScore,
            Integer classRank) {}

    public record ClassroomComparison(
            UUID classroomId,
            String classroomName,
            BigDecimal avgNet,
            String bestSubject,
            String worstSubject) {}

    public record TenantClassroomOverview(
            UUID classroomId,
            String classroomName,
            UUID examId,
            String examTitle,
            BigDecimal avgNet,
            BigDecimal participationRate,
            String bestSubject,
            BigDecimal bestSubjectNet,
            String worstSubject,
            BigDecimal worstSubjectNet,
            long upTrendCount,
            long downTrendCount,
            long stableTrendCount) {}

    // ── Araç davranışları ────────────────────────────────────────────────────

    /** Sınıf-ders deneme fact'leri. examId yoksa bu sınıfın en güncel denemesi kullanılır. */
    public List<ClassroomSubjectTrend> classSubjectTrends(String tenantSchema, UUID classroomId,
                                                          UUID examId, UUID subjectId) {
        tenantSchemaService.switchTo(tenantSchema);
        UUID effectiveExamId = examId != null ? examId : latestExamWithTrends(tenantSchema, classroomId);
        if (effectiveExamId == null) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder("""
                SELECT cst.classroom_id, cst.exam_id, cst.subject_id,
                       c.name AS classroom_name, s.name AS subject_name,
                       e.title AS exam_title, e.exam_date,
                       cst.avg_net, cst.avg_score, cst.student_count,
                       cst.participation_rate, cst.delta_from_previous, cst.trend_direction
                FROM %1$s.class_subject_trends cst
                JOIN %1$s.classrooms c ON c.id = cst.classroom_id
                JOIN %1$s.subjects s ON s.id = cst.subject_id
                JOIN %1$s.exams e ON e.id = cst.exam_id
                WHERE cst.classroom_id = ? AND cst.exam_id = ?
                """.formatted(tenantSchema));
        List<Object> params = new ArrayList<>(List.of(classroomId, effectiveExamId));
        if (subjectId != null) {
            sql.append(" AND cst.subject_id = ?");
            params.add(subjectId);
        }
        sql.append(" ORDER BY s.name");

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new ClassroomSubjectTrend(
                toUuid(rs.getObject("classroom_id")),
                toUuid(rs.getObject("exam_id")),
                toUuid(rs.getObject("subject_id")),
                rs.getString("classroom_name"),
                rs.getString("subject_name"),
                rs.getString("exam_title"),
                toDateString(rs.getObject("exam_date")),
                rs.getBigDecimal("avg_net"),
                rs.getBigDecimal("avg_score"),
                rs.getInt("student_count"),
                rs.getBigDecimal("participation_rate"),
                rs.getBigDecimal("delta_from_previous"),
                rs.getString("trend_direction")), params.toArray());
    }

    /** Zayıf öğrenciler (deneme ortalama net'i < eşik). examId yoksa en güncel sonuç alınır. */
    public List<AtRiskStudent> atRiskStudents(String tenantSchema, UUID classroomId,
                                              UUID examId, Double netThreshold) {
        tenantSchemaService.switchTo(tenantSchema);
        UUID effectiveExamId = examId != null ? examId : latestExamWithResults(tenantSchema, classroomId);
        if (effectiveExamId == null) {
            return List.of();
        }
        double threshold = netThreshold != null && netThreshold > 0 ? netThreshold : defaultAtRiskNetThreshold;

        String sql = """
                SELECT ser.student_id, u.full_name, ser.total_score, ser.class_rank,
                       AVG(ssr.net_score) AS avg_net
                FROM %1$s.student_exam_results ser
                JOIN %1$s.users u ON u.id = ser.student_id
                JOIN %1$s.student_subject_results ssr
                     ON ssr.student_id = ser.student_id AND ssr.exam_id = ser.exam_id
                WHERE ser.exam_id = ?
                  AND ser.student_id IN (SELECT student_id FROM %1$s.classroom_students WHERE classroom_id = ?)
                GROUP BY ser.student_id, u.full_name, ser.total_score, ser.class_rank
                HAVING AVG(ssr.net_score) < ?
                ORDER BY AVG(ssr.net_score) ASC, ser.class_rank ASC NULLS LAST
                """.formatted(tenantSchema);

        return jdbcTemplate.query(sql, (rs, rowNum) -> new AtRiskStudent(
                toUuid(rs.getObject("student_id")),
                rs.getString("full_name"),
                rs.getBigDecimal("avg_net"),
                rs.getBigDecimal("total_score"),
                rs.getObject("class_rank") != null ? rs.getInt("class_rank") : null),
                effectiveExamId, classroomId, threshold);
    }

    /** Sınıf sınıf karşılaştırma özeti. examId yoksa bu sınıflarda son deneme kullanılır. */
    public List<ClassroomComparison> compareClassrooms(String tenantSchema, List<UUID> classroomIds,
                                                       UUID examId) {
        tenantSchemaService.switchTo(tenantSchema);
        if (classroomIds == null || classroomIds.isEmpty()) {
            return List.of();
        }
        UUID effectiveExamId = examId != null ? examId : latestExamAmongClassrooms(tenantSchema, classroomIds);
        if (effectiveExamId == null) {
            return List.of();
        }
        String inClause = "?,".repeat(classroomIds.size());
        inClause = inClause.substring(0, inClause.length() - 1);

        String sql = """
                SELECT cst.classroom_id, c.name AS classroom_name, AVG(cst.avg_net) AS avg_net,
                       (SELECT s2.name FROM %1$s.class_subject_trends t2
                          JOIN %1$s.subjects s2 ON s2.id = t2.subject_id
                         WHERE t2.classroom_id = cst.classroom_id AND t2.exam_id = cst.exam_id
                         ORDER BY t2.avg_net DESC LIMIT 1) AS best_subject,
                       (SELECT s3.name FROM %1$s.class_subject_trends t3
                          JOIN %1$s.subjects s3 ON s3.id = t3.subject_id
                         WHERE t3.classroom_id = cst.classroom_id AND t3.exam_id = cst.exam_id
                         ORDER BY t3.avg_net ASC LIMIT 1) AS worst_subject
                FROM %1$s.class_subject_trends cst
                JOIN %1$s.classrooms c ON c.id = cst.classroom_id
                WHERE cst.exam_id = ? AND cst.classroom_id IN (%2$s)
                GROUP BY cst.classroom_id, c.name
                ORDER BY AVG(cst.avg_net) DESC
                """.formatted(tenantSchema, inClause);

        List<Object> params = new ArrayList<>(List.of(effectiveExamId));
        params.addAll(classroomIds);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ClassroomComparison(
                toUuid(rs.getObject("classroom_id")),
                rs.getString("classroom_name"),
                rs.getBigDecimal("avg_net"),
                rs.getString("best_subject"),
                rs.getString("worst_subject")), params.toArray());
    }

    /**
     * Tenant geneli dashboard özeti (Faz E): her sınıfın son denemesindeki ortalama net,
     * katılım, en güçlü/zayıf ders ve trend sayıları. İstektene bazlı değil; tüm sınıflar.
     */
    public List<TenantClassroomOverview> getTenantOverview(String tenantSchema) {
        tenantSchemaService.switchTo(tenantSchema);

        String sql = """
                WITH latest AS (
                    SELECT DISTINCT ON (t.classroom_id) t.classroom_id, t.exam_id
                    FROM %1$s.class_subject_trends t
                    JOIN %1$s.exams e ON e.id = t.exam_id
                    ORDER BY t.classroom_id, e.exam_date DESC, t.exam_id DESC
                )
                SELECT l.classroom_id, c.name AS classroom_name, l.exam_id, e.title AS exam_title,
                       ROUND(AVG(t.avg_net), 2) AS avg_net,
                       ROUND(AVG(t.participation_rate), 2) AS participation_rate,
                       (SELECT s2.name FROM %1$s.class_subject_trends t2
                          JOIN %1$s.subjects s2 ON s2.id = t2.subject_id
                         WHERE t2.classroom_id = l.classroom_id AND t2.exam_id = l.exam_id
                         ORDER BY t2.avg_net DESC LIMIT 1) AS best_subject,
                       (SELECT MAX(t3.avg_net) FROM %1$s.class_subject_trends t3
                         WHERE t3.classroom_id = l.classroom_id AND t3.exam_id = l.exam_id) AS best_subject_net,
                       (SELECT s4.name FROM %1$s.class_subject_trends t4
                          JOIN %1$s.subjects s4 ON s4.id = t4.subject_id
                         WHERE t4.classroom_id = l.classroom_id AND t4.exam_id = l.exam_id
                         ORDER BY t4.avg_net ASC LIMIT 1) AS worst_subject,
                       (SELECT MIN(t5.avg_net) FROM %1$s.class_subject_trends t5
                         WHERE t5.classroom_id = l.classroom_id AND t5.exam_id = l.exam_id) AS worst_subject_net,
                       COUNT(*) FILTER (WHERE t.trend_direction = 'UP') AS up_count,
                       COUNT(*) FILTER (WHERE t.trend_direction = 'DOWN') AS down_count,
                       COUNT(*) FILTER (WHERE t.trend_direction = 'STABLE') AS stable_count
                FROM latest l
                JOIN %1$s.classrooms c ON c.id = l.classroom_id
                JOIN %1$s.exams e ON e.id = l.exam_id
                JOIN %1$s.class_subject_trends t ON t.classroom_id = l.classroom_id AND t.exam_id = l.exam_id
                GROUP BY l.classroom_id, c.name, l.exam_id, e.title, e.exam_date
                ORDER BY c.name
                """.formatted(tenantSchema);

        return jdbcTemplate.query(sql, (rs, rowNum) -> new TenantClassroomOverview(
                toUuid(rs.getObject("classroom_id")),
                rs.getString("classroom_name"),
                toUuid(rs.getObject("exam_id")),
                rs.getString("exam_title"),
                rs.getBigDecimal("avg_net"),
                rs.getBigDecimal("participation_rate"),
                rs.getString("best_subject"),
                rs.getBigDecimal("best_subject_net"),
                rs.getString("worst_subject"),
                rs.getBigDecimal("worst_subject_net"),
                rs.getLong("up_count"),
                rs.getLong("down_count"),
                rs.getLong("stable_count")));
    }

    // ── Varsayılan deneme seçimi ─────────────────────────────────────────────

    private UUID latestExamWithTrends(String tenantSchema, UUID classroomId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT cst.exam_id
                    FROM %1$s.class_subject_trends cst
                    JOIN %1$s.exams e ON e.id = cst.exam_id
                    WHERE cst.classroom_id = ?
                    ORDER BY e.exam_date DESC, cst.calculated_at DESC
                    LIMIT 1
                    """.formatted(tenantSchema),
                    UUID.class, classroomId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private UUID latestExamWithResults(String tenantSchema, UUID classroomId) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT ser.exam_id
                    FROM %1$s.student_exam_results ser
                    JOIN %1$s.exams e ON e.id = ser.exam_id
                    WHERE ser.student_id IN
                          (SELECT student_id FROM %1$s.classroom_students WHERE classroom_id = ?)
                    ORDER BY e.exam_date DESC
                    LIMIT 1
                    """.formatted(tenantSchema),
                    UUID.class, classroomId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private UUID latestExamAmongClassrooms(String tenantSchema, List<UUID> classroomIds) {
        String inClause = "?,".repeat(classroomIds.size());
        inClause = inClause.substring(0, inClause.length() - 1);
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT cst.exam_id
                    FROM %1$s.class_subject_trends cst
                    JOIN %1$s.exams e ON e.id = cst.exam_id
                    WHERE cst.classroom_id IN (%2$s)
                    ORDER BY e.exam_date DESC
                    LIMIT 1
                    """.formatted(tenantSchema, inClause),
                    UUID.class, classroomIds.toArray());
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    // ── Yardımcılar ──────────────────────────────────────────────────────────

    private UUID toUuid(Object value) {
        if (value == null) return null;
        return value instanceof UUID u ? u : UUID.fromString(value.toString());
    }

    private String toDateString(Object value) {
        return value == null ? null : value.toString();
    }
}
