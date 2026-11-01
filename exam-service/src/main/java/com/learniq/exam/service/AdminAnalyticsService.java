package com.learniq.exam.service;

import com.learniq.common.tenant.TenantSchemaValidator;
import com.learniq.exam.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * Tenant-scale admin analytics.
 * Tüm SQL'ler şema-niteliklidir (tenant.tablo) — SET search_path'e güvenilmez.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAnalyticsService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard(String tenantId) {
        String schema = schema(tenantId);
        List<ClassroomAdminSummary> rows = jdbcTemplate.query(
                DASHBOARD_SQL.formatted(schema, schema, schema, schema, schema, schema, schema, schema, schema, schema, schema),
                (rs, i) -> {
                    ClassroomAdminSummary s = new ClassroomAdminSummary();
                    s.setClassroomId((UUID) rs.getObject("classroom_id"));
                    s.setClassroomName(rs.getString("classroom_name"));
                    s.setLatestExamId((UUID) rs.getObject("latest_exam_id"));
                    s.setLatestExamTitle(rs.getString("latest_exam_title"));
                    Date examDate = rs.getDate("latest_exam_date");
                    s.setLatestExamDate(examDate != null ? examDate.toLocalDate() : null);
                    s.setAvgNet(rs.getBigDecimal("avg_net"));
                    s.setParticipationRate(rs.getBigDecimal("participation_rate"));
                    s.setBestSubjectName(rs.getString("best_subject_name"));
                    s.setBestSubjectNet(rs.getBigDecimal("best_subject_net"));
                    s.setWorstSubjectName(rs.getString("worst_subject_name"));
                    s.setWorstSubjectNet(rs.getBigDecimal("worst_subject_net"));
                    s.setUpTrendCount(rs.getInt("up_count"));
                    s.setDownTrendCount(rs.getInt("down_count"));
                    s.setStableTrendCount(rs.getInt("stable_count"));
                    return s;
                });
        return AdminDashboardResponse.builder()
                .classroomCount(rows.size())
                .classrooms(rows)
                .build();
    }

    @Transactional(readOnly = true)
    public SubjectScorecardResponse getSubjectScorecard(String tenantId, UUID examId) {
        String schema = schema(tenantId);
        UUID effectiveExamId = examId;
        if (effectiveExamId == null) {
            try {
                effectiveExamId = jdbcTemplate.queryForObject(
                        LATEST_TENANT_EXAM_SQL.formatted(schema, schema), UUID.class);
            } catch (EmptyResultDataAccessException e) {
                return SubjectScorecardResponse.builder().examId(null).subjects(List.of()).build();
            }
        }
        if (effectiveExamId == null) {
            return SubjectScorecardResponse.builder().examId(null).subjects(List.of()).build();
        }
        List<SubjectScorecardRow> rows = jdbcTemplate.query(SCORECARD_SQL.formatted(schema, schema, schema, schema, schema, schema),
                (rs, i) -> SubjectScorecardRow.builder()
                        .subjectId((UUID) rs.getObject("subject_id"))
                        .subjectName(rs.getString("subject_name"))
                        .classroomCount(rs.getInt("classroom_count"))
                        .avgNet(rs.getBigDecimal("avg_net"))
                        .minNet(rs.getBigDecimal("min_net"))
                        .maxNet(rs.getBigDecimal("max_net"))
                        .bestClassroomName(rs.getString("best_classroom_name"))
                        .worstClassroomName(rs.getString("worst_classroom_name"))
                        .build(),
                effectiveExamId);
        return SubjectScorecardResponse.builder()
                .examId(effectiveExamId)
                .subjects(rows)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ClassSubjectTrendRow> getTrends(String tenantId, UUID subjectId, int limit) {
        String schema = schema(tenantId);
        String sql = subjectId != null ? TRENDS_BY_SUBJECT_SQL.formatted(schema, schema, schema, schema)
                : TRENDS_SQL.formatted(schema, schema, schema, schema);
        return jdbcTemplate.query(sql, (rs, i) -> ClassSubjectTrendRow.builder()
                        .classroomName(rs.getString("classroom_name"))
                        .subjectName(rs.getString("subject_name"))
                        .examTitle(rs.getString("exam_title"))
                        .examDate(rs.getDate("exam_date") != null ? rs.getDate("exam_date").toLocalDate() : null)
                        .avgNet(rs.getBigDecimal("avg_net"))
                        .deltaFromPrevious(rs.getBigDecimal("delta_from_previous"))
                        .trendDirection(rs.getString("trend_direction"))
                        .build(),
                subjectId != null ? new Object[]{subjectId, limit} : new Object[]{limit});
    }

    @Transactional(readOnly = true)
    public List<RecentObservationDto> getRecentObservations(String tenantId, int limit) {
        String schema = schema(tenantId);
        return jdbcTemplate.query(RECENT_OBSERVATIONS_SQL.formatted(schema, schema, schema), (rs, i) -> {
            RecentObservationDto dto = new RecentObservationDto();
            dto.setObservationId((UUID) rs.getObject("observation_id"));
            dto.setClassroomName(rs.getString("classroom_name"));
            dto.setSubjectName(rs.getString("subject_name"));
            dto.setTeacherRef((UUID) rs.getObject("teacher_ref"));
            dto.setTags(rs.getString("tags"));
            dto.setContent(rs.getString("content"));
            Timestamp ts = rs.getTimestamp("observed_at");
            dto.setObservedAt(ts != null ? ts.toLocalDateTime() : null);
            return dto;
        }, limit);
    }

    private String schema(String tenantId) {
        return TenantSchemaValidator.requireValid(tenantId);
    }

    private static final String DASHBOARD_SQL = """
            WITH latest AS (
                SELECT DISTINCT ON (t.classroom_id) t.classroom_id, t.exam_id
                FROM %s.class_subject_trends t
                JOIN %s.exams e ON e.id = t.exam_id
                ORDER BY t.classroom_id, e.exam_date DESC, t.exam_id DESC
            )
            SELECT l.classroom_id,
                   c.name AS classroom_name,
                   l.exam_id AS latest_exam_id,
                   e.title AS latest_exam_title,
                   e.exam_date AS latest_exam_date,
                   ROUND(AVG(t.avg_net), 2) AS avg_net,
                   ROUND(AVG(t.participation_rate), 2) AS participation_rate,
                   (SELECT s.name
                      FROM %s.class_subject_trends t2
                      JOIN %s.subjects s ON s.id = t2.subject_id
                     WHERE t2.classroom_id = l.classroom_id AND t2.exam_id = l.exam_id
                     ORDER BY t2.avg_net DESC
                     LIMIT 1) AS best_subject_name,
                   (SELECT MAX(t2.avg_net)
                      FROM %s.class_subject_trends t2
                     WHERE t2.classroom_id = l.classroom_id AND t2.exam_id = l.exam_id) AS best_subject_net,
                   (SELECT s.name
                      FROM %s.class_subject_trends t2
                      JOIN %s.subjects s ON s.id = t2.subject_id
                     WHERE t2.classroom_id = l.classroom_id AND t2.exam_id = l.exam_id
                     ORDER BY t2.avg_net ASC
                     LIMIT 1) AS worst_subject_name,
                   (SELECT MIN(t2.avg_net)
                      FROM %s.class_subject_trends t2
                     WHERE t2.classroom_id = l.classroom_id AND t2.exam_id = l.exam_id) AS worst_subject_net,
                   COUNT(*) FILTER (WHERE t.trend_direction = 'UP') AS up_count,
                   COUNT(*) FILTER (WHERE t.trend_direction = 'DOWN') AS down_count,
                   COUNT(*) FILTER (WHERE t.trend_direction = 'STABLE') AS stable_count
            FROM latest l
            JOIN %s.classrooms c ON c.id = l.classroom_id
            JOIN %s.exams e ON e.id = l.exam_id
            JOIN %s.class_subject_trends t ON t.classroom_id = l.classroom_id AND t.exam_id = l.exam_id
            GROUP BY l.classroom_id, c.name, l.exam_id, e.title, e.exam_date
            ORDER BY c.name
            """;

    private static final String LATEST_TENANT_EXAM_SQL = """
            SELECT t.exam_id
            FROM %s.class_subject_trends t
            JOIN %s.exams e ON e.id = t.exam_id
            ORDER BY e.exam_date DESC, t.exam_id DESC
            LIMIT 1
            """;

    private static final String SCORECARD_SQL = """
            SELECT t.subject_id,
                   s.name AS subject_name,
                   COUNT(DISTINCT t.classroom_id) AS classroom_count,
                   ROUND(AVG(t.avg_net), 2) AS avg_net,
                   MIN(t.avg_net) AS min_net,
                   MAX(t.avg_net) AS max_net,
                   (SELECT c.name
                      FROM %s.class_subject_trends t2
                      JOIN %s.classrooms c ON c.id = t2.classroom_id
                     WHERE t2.subject_id = t.subject_id AND t2.exam_id = t.exam_id
                     ORDER BY t2.avg_net DESC
                     LIMIT 1) AS best_classroom_name,
                   (SELECT c.name
                      FROM %s.class_subject_trends t2
                      JOIN %s.classrooms c ON c.id = t2.classroom_id
                     WHERE t2.subject_id = t.subject_id AND t2.exam_id = t.exam_id
                     ORDER BY t2.avg_net ASC
                     LIMIT 1) AS worst_classroom_name
            FROM %s.class_subject_trends t
            JOIN %s.subjects s ON s.id = t.subject_id
            WHERE t.exam_id = ?
            GROUP BY t.subject_id, s.name, t.exam_id
            ORDER BY AVG(t.avg_net) DESC
            """;

    private static final String TRENDS_SQL = """
            SELECT c.name AS classroom_name, s.name AS subject_name, e.title AS exam_title, e.exam_date,
                   t.avg_net, t.delta_from_previous, t.trend_direction
            FROM %s.class_subject_trends t
            JOIN %s.classrooms c ON c.id = t.classroom_id
            JOIN %s.subjects s ON s.id = t.subject_id
            JOIN %s.exams e ON e.id = t.exam_id
            ORDER BY e.exam_date DESC, t.exam_id DESC, c.name, s.name
            LIMIT ?
            """;

    private static final String TRENDS_BY_SUBJECT_SQL = """
            SELECT c.name AS classroom_name, s.name AS subject_name, e.title AS exam_title, e.exam_date,
                   t.avg_net, t.delta_from_previous, t.trend_direction
            FROM %s.class_subject_trends t
            JOIN %s.classrooms c ON c.id = t.classroom_id
            JOIN %s.subjects s ON s.id = t.subject_id
            JOIN %s.exams e ON e.id = t.exam_id
            WHERE t.subject_id = ?
            ORDER BY e.exam_date DESC, t.exam_id DESC, c.name, s.name
            LIMIT ?
            """;

    private static final String RECENT_OBSERVATIONS_SQL = """
            SELECT o.id AS observation_id, c.name AS classroom_name, s.name AS subject_name,
                   o.teacher_id AS teacher_ref, o.tags::text AS tags, o.content, o.created_at AS observed_at
            FROM %s.teacher_observations o
            LEFT JOIN %s.classrooms c ON c.id = o.classroom_id
            LEFT JOIN %s.subjects s ON s.id = o.subject_id
            ORDER BY o.created_at DESC
            LIMIT ?
            """;
}
