package com.learniq.exam.service;

import com.learniq.exam.domain.Exam;
import com.learniq.exam.domain.StudentBadge;
import com.learniq.exam.domain.StudentExamResult;
import com.learniq.exam.dto.StudentHomeResponseDto;
import com.learniq.exam.repository.StudentBadgeRepository;
import com.learniq.exam.repository.StudentExamResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentHomeService {

    private final StudentExamResultRepository studentExamResultRepository;
    private final StudentBadgeRepository studentBadgeRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public StudentHomeResponseDto getStudentHomeData(UUID studentId) {
        // 1. Fetch Student & Classroom Info using JDBC (tenant schema borders)
        String sql = """
            SELECT u.full_name, c.name as classroom_name
            FROM users u
            LEFT JOIN classroom_students cs ON u.id = cs.student_id
            LEFT JOIN classrooms c ON cs.classroom_id = c.id
            WHERE u.id = :studentId
            LIMIT 1
        """;
        
        List<Map<String, Object>> userInfo = jdbcTemplate.queryForList(sql, Map.of("studentId", studentId));
        String studentName = "Öğrenci";
        String classroomName = "Atanmamış";
        if (!userInfo.isEmpty()) {
            Map<String, Object> row = userInfo.get(0);
            if (row.get("full_name") != null) {
                studentName = (String) row.get("full_name");
            }
            if (row.get("classroom_name") != null) {
                classroomName = (String) row.get("classroom_name");
            }
        }

        // 2. Fetch Latest Finalized Exam
        List<StudentExamResult> latestResults = studentExamResultRepository.findAll().stream()
                .filter(r -> r.getStudentId().equals(studentId))
                .filter(r -> r.getStatus() == StudentExamResult.ResultStatus.APPROVED)
                .filter(r -> r.getExam().getStatus() == Exam.ExamStatus.FINALIZED)
                .sorted((r1, r2) -> r2.getExam().getExamDate().compareTo(r1.getExam().getExamDate()))
                .limit(1)
                .collect(Collectors.toList());

        StudentHomeResponseDto.LatestExamDto latestExamDto = null;
        if (!latestResults.isEmpty()) {
            StudentExamResult lr = latestResults.get(0);
            latestExamDto = StudentHomeResponseDto.LatestExamDto.builder()
                    .examId(lr.getExam().getId())
                    .title(lr.getExam().getTitle())
                    .examDate(lr.getExam().getExamDate())
                    .netScore(lr.getTotalScore())
                    .percentile(lr.getPercentile())
                    .build();
        }

        // 3. Fetch Recent Badges (Last 5)
        List<StudentBadge> badges = studentBadgeRepository.findLatestBadgesByStudentId(studentId);
        List<StudentHomeResponseDto.BadgeDto> badgeDtos = badges.stream().limit(5).map(sb -> 
                StudentHomeResponseDto.BadgeDto.builder()
                        .code(sb.getBadge().getCode())
                        .title(sb.getBadge().getTitle())
                        .description(sb.getBadge().getDescription())
                        .examTitle(sb.getExam() != null ? sb.getExam().getTitle() : null)
                        .awardedAt(sb.getAwardedAt())
                        .build()
        ).collect(Collectors.toList());

        return StudentHomeResponseDto.builder()
                .studentName(studentName)
                .classroomName(classroomName)
                .latestExam(latestExamDto)
                .recentBadges(badgeDtos)
                .build();
    }
}
