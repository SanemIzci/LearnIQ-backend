package com.learniq.exam.service;

import com.learniq.exam.dto.*;
import com.learniq.common.tenant.TenantContext;
import com.learniq.common.tenant.TenantSchemaValidator;
import com.learniq.exam.exception.EntityNotFoundException;
import com.learniq.exam.security.AccessControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherAnalyticsService {

    private final JdbcTemplate jdbcTemplate;
    private final AccessControlService accessControlService;

    private String schema() {
        return TenantSchemaValidator.requireValid(TenantContext.getCurrentTenant());
    }

    /**
     * Sınıf Başarı Karnesi (Classroom Analytics).
     * Retrieves overall classroom performance metrics, subject breakdown, and recent exam trends.
     */
    @Transactional(readOnly = true)
    public ClassroomAnalyticsResponse getClassroomAnalytics(
            String tenantId,
            UUID requesterId,
            String role,
            UUID classroomId,
            UUID examId) {

        accessControlService.validateClassroomAccess(tenantId, requesterId, role, classroomId);
        String schema = schema();

        String classroomName;
        try {
            classroomName = jdbcTemplate.queryForObject(
                    "SELECT name FROM %s.classrooms WHERE id = ?".formatted(schema),
                    String.class,
                    classroomId);
        } catch (EmptyResultDataAccessException e) {
            throw new EntityNotFoundException("Classroom", classroomId);
        }

        Integer totalStudents = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM %s.classroom_students WHERE classroom_id = ?".formatted(schema),
                Integer.class,
                classroomId);
        int totalStudentCount = totalStudents != null ? totalStudents : 0;

        // If examId is not provided, pick the latest exam with approved results for this classroom
        ExamSummaryDto examSummary = null;
        if (examId != null) {
            try {
                examSummary = jdbcTemplate.queryForObject(
                        "SELECT id, title, exam_date, exam_type FROM %s.exams WHERE id = ?"
                                .formatted(schema),
                        (rs, rowNum) -> ExamSummaryDto.builder()
                                .id(UUID.fromString(rs.getString("id")))
                                .title(rs.getString("title"))
                                .examDate(toLocalDate(rs.getDate("exam_date")))
                                .examType(rs.getString("exam_type"))
                                .build(),
                        examId);
            } catch (EmptyResultDataAccessException e) {
                throw new EntityNotFoundException("Exam", examId);
            }
        } else {
            List<ExamSummaryDto> latestExams = jdbcTemplate.query(
                    """
                    SELECT e.id, e.title, e.exam_date, e.exam_type
                    FROM %1$s.exams e
                    WHERE e.id IN (
                        SELECT DISTINCT ser.exam_id
                        FROM %1$s.student_exam_results ser
                        JOIN %1$s.classroom_students cs ON cs.student_id = ser.student_id
                        WHERE cs.classroom_id = ? AND ser.status = 'APPROVED'
                    )
                    ORDER BY e.exam_date DESC, e.created_at DESC
                    LIMIT 1
                    """.formatted(schema),
                    (rs, rowNum) -> ExamSummaryDto.builder()
                            .id(UUID.fromString(rs.getString("id")))
                            .title(rs.getString("title"))
                            .examDate(toLocalDate(rs.getDate("exam_date")))
                            .examType(rs.getString("exam_type"))
                            .build(),
                    classroomId);

            if (!latestExams.isEmpty()) {
                examSummary = latestExams.get(0);
                examId = examSummary.getId();
            }
        }

        // Fetch recent exam trends (last 5 exams for this classroom)
        List<ClassExamTrendDto> recentTrends = jdbcTemplate.query(
                """
                SELECT e.id, e.title, e.exam_date, AVG(ser.total_score) as avg_score, count(ser.id) as part_count
                FROM %1$s.exams e
                JOIN %1$s.student_exam_results ser ON ser.exam_id = e.id
                JOIN %1$s.classroom_students cs ON cs.student_id = ser.student_id
                WHERE cs.classroom_id = ? AND ser.status = 'APPROVED'
                GROUP BY e.id, e.title, e.exam_date, e.created_at
                ORDER BY e.exam_date DESC, e.created_at DESC
                LIMIT 5
                """.formatted(schema),
                (rs, rowNum) -> ClassExamTrendDto.builder()
                        .examId(UUID.fromString(rs.getString("id")))
                        .examTitle(rs.getString("title"))
                        .examDate(toLocalDate(rs.getDate("exam_date")))
                        .avgScore(round(rs.getBigDecimal("avg_score")))
                        .studentCount(rs.getInt("part_count"))
                        .build(),
                classroomId);

        if (examSummary == null) {
            return ClassroomAnalyticsResponse.builder()
                    .classroomId(classroomId)
                    .classroomName(classroomName)
                    .totalStudents(totalStudentCount)
                    .analyzedExam(null)
                    .participationCount(0)
                    .participationRate(BigDecimal.ZERO)
                    .classAverageScore(BigDecimal.ZERO)
                    .subjectAverages(List.of())
                    .recentExamTrends(recentTrends)
                    .build();
        }

        // Participation & class average score for the selected exam
        Map<String, Object> examStats = jdbcTemplate.queryForMap(
                """
                SELECT count(ser.id) as part_count, AVG(ser.total_score) as avg_score
                FROM %1$s.student_exam_results ser
                JOIN %1$s.classroom_students cs ON cs.student_id = ser.student_id
                WHERE cs.classroom_id = ? AND ser.exam_id = ? AND ser.status = 'APPROVED'
                """.formatted(schema),
                classroomId, examId);

        Number partCountNum = (Number) examStats.get("part_count");
        int participationCount = partCountNum != null ? partCountNum.intValue() : 0;

        Number avgScoreNum = (Number) examStats.get("avg_score");
        BigDecimal classAverageScore = avgScoreNum != null
                ? BigDecimal.valueOf(avgScoreNum.doubleValue()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal participationRate = totalStudentCount > 0
                ? BigDecimal.valueOf((participationCount * 100.0) / totalStudentCount).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Subject breakdown
        List<ClassSubjectAverageDto> subjectAverages = jdbcTemplate.query(
                """
                SELECT s.id as subject_id, s.name as subject_name,
                       AVG(ssr.correct_count) as avg_correct,
                       AVG(ssr.wrong_count) as avg_wrong,
                       AVG(ssr.net_score) as avg_net
                FROM %1$s.student_subject_results ssr
                JOIN %1$s.subjects s ON s.id = ssr.subject_id
                JOIN %1$s.classroom_students cs ON cs.student_id = ssr.student_id
                JOIN %1$s.student_exam_results ser ON ser.student_id = ssr.student_id AND ser.exam_id = ssr.exam_id
                WHERE cs.classroom_id = ? AND ssr.exam_id = ? AND ser.status = 'APPROVED'
                GROUP BY s.id, s.name
                ORDER BY s.name
                """.formatted(schema),
                (rs, rowNum) -> ClassSubjectAverageDto.builder()
                        .subjectId(UUID.fromString(rs.getString("subject_id")))
                        .subjectName(rs.getString("subject_name"))
                        .avgCorrect(round(rs.getBigDecimal("avg_correct")))
                        .avgWrong(round(rs.getBigDecimal("avg_wrong")))
                        .avgNet(round(rs.getBigDecimal("avg_net")))
                        .build(),
                classroomId, examId);

        return ClassroomAnalyticsResponse.builder()
                .classroomId(classroomId)
                .classroomName(classroomName)
                .totalStudents(totalStudentCount)
                .analyzedExam(examSummary)
                .participationCount(participationCount)
                .participationRate(participationRate)
                .classAverageScore(classAverageScore)
                .subjectAverages(subjectAverages)
                .recentExamTrends(recentTrends)
                .build();
    }

    /**
     * Şube Karşılaştırma API'si (Classroom Comparison).
     * Compares 2 or more classrooms on a given exam.
     */
    @Transactional(readOnly = true)
    public ClassroomComparisonResponse compareClassrooms(
            String tenantId,
            UUID requesterId,
            String role,
            List<UUID> classroomIds,
            UUID examId) {

        if (classroomIds == null || classroomIds.size() < 2) {
            throw new IllegalArgumentException("Comparison requires at least two classrooms.");
        }

        // Validate access for all requested classrooms
        for (UUID cid : classroomIds) {
            accessControlService.validateClassroomAccess(tenantId, requesterId, role, cid);
        }

        String schema = schema();

        // Find exam if not specified
        ExamSummaryDto examSummary;
        if (examId != null) {
            try {
                examSummary = jdbcTemplate.queryForObject(
                        "SELECT id, title, exam_date, exam_type FROM %s.exams WHERE id = ?"
                                .formatted(schema),
                        (rs, rowNum) -> ExamSummaryDto.builder()
                                .id(UUID.fromString(rs.getString("id")))
                                .title(rs.getString("title"))
                                .examDate(toLocalDate(rs.getDate("exam_date")))
                                .examType(rs.getString("exam_type"))
                                .build(),
                        examId);
            } catch (EmptyResultDataAccessException e) {
                throw new EntityNotFoundException("Exam", examId);
            }
        } else {
            String inSql = String.join(",", Collections.nCopies(classroomIds.size(), "?"));
            List<ExamSummaryDto> candidateExams = jdbcTemplate.query(
                    """
                    SELECT e.id, e.title, e.exam_date, e.exam_type
                    FROM %1$s.exams e
                    WHERE e.id IN (
                        SELECT DISTINCT ser.exam_id
                        FROM %1$s.student_exam_results ser
                        JOIN %1$s.classroom_students cs ON cs.student_id = ser.student_id
                        WHERE cs.classroom_id IN (%2$s) AND ser.status = 'APPROVED'
                    )
                    ORDER BY e.exam_date DESC, e.created_at DESC
                    LIMIT 1
                    """.formatted(schema, inSql),
                    (rs, rowNum) -> ExamSummaryDto.builder()
                            .id(UUID.fromString(rs.getString("id")))
                            .title(rs.getString("title"))
                            .examDate(toLocalDate(rs.getDate("exam_date")))
                            .examType(rs.getString("exam_type"))
                            .build(),
                    classroomIds.toArray());

            if (candidateExams.isEmpty()) {
                throw new IllegalArgumentException("No common exam results found to compare the specified classrooms.");
            }
            examSummary = candidateExams.get(0);
            examId = examSummary.getId();
        }

        List<ClassroomComparisonItemDto> comparisonItems = new ArrayList<>();

        for (UUID cid : classroomIds) {
            String classroomName = jdbcTemplate.queryForObject(
                    "SELECT name FROM %s.classrooms WHERE id = ?".formatted(schema),
                    String.class,
                    cid);

            Integer totalStudents = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM %s.classroom_students WHERE classroom_id = ?".formatted(schema),
                    Integer.class,
                    cid);

            Map<String, Object> examStats = jdbcTemplate.queryForMap(
                    """
                    SELECT count(ser.id) as part_count, AVG(ser.total_score) as avg_score
                    FROM student_exam_results ser
                    JOIN classroom_students cs ON cs.student_id = ser.student_id
                    WHERE cs.classroom_id = ? AND ser.exam_id = ? AND ser.status = 'APPROVED'
                    """,
                    cid, examId);

            Number partCountNum = (Number) examStats.get("part_count");
            int participationCount = partCountNum != null ? partCountNum.intValue() : 0;

            Number avgScoreNum = (Number) examStats.get("avg_score");
            BigDecimal avgScore = avgScoreNum != null
                    ? BigDecimal.valueOf(avgScoreNum.doubleValue()).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            List<ClassSubjectAverageDto> subjectNets = jdbcTemplate.query(
                    """
                    SELECT s.id as subject_id, s.name as subject_name,
                           AVG(ssr.correct_count) as avg_correct,
                           AVG(ssr.wrong_count) as avg_wrong,
                           AVG(ssr.net_score) as avg_net
                    FROM student_subject_results ssr
                    JOIN subjects s ON s.id = ssr.subject_id
                    JOIN classroom_students cs ON cs.student_id = ssr.student_id
                    JOIN student_exam_results ser ON ser.student_id = ssr.student_id AND ser.exam_id = ssr.exam_id
                    WHERE cs.classroom_id = ? AND ssr.exam_id = ? AND ser.status = 'APPROVED'
                    GROUP BY s.id, s.name
                    ORDER BY s.name
                    """,
                    (rs, rowNum) -> ClassSubjectAverageDto.builder()
                            .subjectId(UUID.fromString(rs.getString("subject_id")))
                            .subjectName(rs.getString("subject_name"))
                            .avgCorrect(round(rs.getBigDecimal("avg_correct")))
                            .avgWrong(round(rs.getBigDecimal("avg_wrong")))
                            .avgNet(round(rs.getBigDecimal("avg_net")))
                            .build(),
                    cid, examId);

            comparisonItems.add(ClassroomComparisonItemDto.builder()
                    .classroomId(cid)
                    .classroomName(classroomName)
                    .totalStudents(totalStudents != null ? totalStudents : 0)
                    .participationCount(participationCount)
                    .avgScore(avgScore)
                    .subjectNets(subjectNets)
                    .build());
        }

        return ClassroomComparisonResponse.builder()
                .examId(examSummary.getId())
                .examTitle(examSummary.getTitle())
                .examDate(examSummary.getExamDate())
                .classrooms(comparisonItems)
                .build();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private BigDecimal round(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate toLocalDate(Date sqlDate) {
        return sqlDate != null ? sqlDate.toLocalDate() : null;
    }
}
