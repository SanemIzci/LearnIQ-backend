package com.learniq.exam.service;

import com.learniq.exam.domain.Exam;
import com.learniq.exam.domain.StudentExamResult;
import com.learniq.exam.dto.StudentDashboardResponse;
import com.learniq.exam.dto.TopStudentDto;
import com.learniq.exam.exception.EntityNotFoundException;
import com.learniq.exam.repository.StudentExamResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentDashboardService {

    private final StudentExamResultRepository studentExamResultRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public StudentDashboardResponse getDashboard(UUID examId, UUID studentId) {
        StudentExamResult result = studentExamResultRepository.findByExamIdAndStudentId(examId, studentId)
                .orElseThrow(() -> new EntityNotFoundException("Result for student", studentId));

        if (result.getExam().getStatus() != Exam.ExamStatus.FINALIZED) {
            throw new IllegalStateException("Sınav henüz sonlandırılmadığı için istatistikler ve sıralamalar görüntülenemez.");
        }

        String classSql = "SELECT classroom_id FROM classroom_students WHERE student_id = :studentId";
        List<UUID> classIds = jdbcTemplate.queryForList(classSql, Map.of("studentId", studentId), UUID.class);
        if (classIds.isEmpty()) {
            throw new IllegalStateException("Öğrencinin kayıtlı olduğu bir sınıf bulunamadı.");
        }
        UUID classroomId = classIds.get(0);

        String avgSql = """
            SELECT AVG(r.total_score)
            FROM student_exam_results r
            JOIN classroom_students cs ON r.student_id = cs.student_id
            WHERE r.exam_id = :examId AND cs.classroom_id = :classroomId AND r.status = 'APPROVED'
        """;
        BigDecimal classAverage = jdbcTemplate.queryForObject(avgSql,
                Map.of("examId", examId, "classroomId", classroomId), BigDecimal.class);

        String top3Sql = """
            SELECT r.class_rank, r.total_score, u.full_name, u.leaderboard_consent
            FROM student_exam_results r
            JOIN classroom_students cs ON r.student_id = cs.student_id
            JOIN users u ON r.student_id = u.id
            WHERE r.exam_id = :examId AND cs.classroom_id = :classroomId AND r.status = 'APPROVED'
            ORDER BY r.class_rank ASC, r.created_at ASC
            LIMIT 3
        """;

        List<TopStudentDto> top3 = jdbcTemplate.query(top3Sql,
                Map.of("examId", examId, "classroomId", classroomId),
                (rs, rowNum) -> {
                    boolean consent = rs.getBoolean("leaderboard_consent");
                    String fullName = rs.getString("full_name");
                    String displayName = consent ? formatName(fullName) : "Anonim Öğrenci";

                    return TopStudentDto.builder()
                            .rank(rs.getInt("class_rank"))
                            .netScore(rs.getBigDecimal("total_score"))
                            .displayName(displayName)
                            .build();
                });

        return StudentDashboardResponse.builder()
                .classRank(result.getClassRank())
                .schoolRank(result.getSchoolRank())
                .netScore(result.getTotalScore())
                .percentile(result.getPercentile())
                .classAverage(classAverage != null ? classAverage.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .top3Students(top3)
                .build();
    }

    private String formatName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "Anonim Öğrenci";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) return parts[0].charAt(0) + "***";
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            sb.append(parts[i]).append(" ");
        }
        sb.append(parts[parts.length - 1].charAt(0)).append(".");
        return sb.toString();
    }
}
