package com.learniq.exam.service;

import com.learniq.exam.domain.Exam;
import com.learniq.exam.domain.StudentExamResult;
import com.learniq.exam.dto.TrendDataPointDto;
import com.learniq.exam.repository.StudentExamResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentTrendService {

    private final StudentExamResultRepository studentExamResultRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<TrendDataPointDto> getOverallTrends(UUID studentId) {
        // Genel deneme neti ve yüzdelik dilim trendleri (Sadece FINALIZED sınavlar ve APPROVED sonuçlar)
        List<StudentExamResult> results = studentExamResultRepository.findAll().stream()
                .filter(r -> r.getStudentId().equals(studentId))
                .filter(r -> r.getStatus() == StudentExamResult.ResultStatus.APPROVED)
                .filter(r -> r.getExam().getStatus() == Exam.ExamStatus.FINALIZED)
                .sorted(Comparator.comparing(r -> r.getExam().getExamDate()))
                .collect(Collectors.toList());

        return results.stream().map(r -> TrendDataPointDto.builder()
                .examId(r.getExam().getId())
                .examName(r.getExam().getTitle())
                .examDate(r.getExam().getExamDate())
                .netScore(r.getTotalScore())
                .percentile(r.getPercentile())
                .build()
        ).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TrendDataPointDto> getSubjectTrends(UUID studentId, UUID subjectId) {
        // Ders bazlı (Matematik, Türkçe vb.) net trendi
        String sql = """
            SELECT e.id, e.title, e.exam_date, sr.net_score
            FROM student_subject_results sr
            JOIN exams e ON sr.exam_id = e.id
            JOIN student_exam_results er ON er.exam_id = e.id AND er.student_id = sr.student_id
            WHERE sr.student_id = :studentId 
              AND sr.subject_id = :subjectId 
              AND e.status = 'FINALIZED' 
              AND er.status = 'APPROVED'
            ORDER BY e.exam_date ASC
        """;

        return jdbcTemplate.query(sql, Map.of("studentId", studentId, "subjectId", subjectId), (rs, rowNum) ->
                TrendDataPointDto.builder()
                        .examId(UUID.fromString(rs.getString("id")))
                        .examName(rs.getString("title"))
                        .examDate(rs.getDate("exam_date").toLocalDate())
                        .netScore(rs.getBigDecimal("net_score"))
                        .percentile(null) // Ders bazında yüzdelik dilim hesaplamıyoruz
                        .build()
        );
    }
}
