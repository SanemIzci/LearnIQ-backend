package com.learniq.exam.service;

import com.learniq.exam.domain.Exam;
import com.learniq.exam.domain.StudentExamResult;
import com.learniq.exam.dto.TrendDataPointDto;
import com.learniq.exam.repository.StudentExamResultRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentTrendServiceTest {

    @Mock
    private StudentExamResultRepository studentExamResultRepository;

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private StudentTrendService studentTrendService;

    @Test
    @DisplayName("getOverallTrends - Öğrencinin genel net trendini tarihe göre sıralı döndürür")
    void getOverallTrends_success() {
        UUID studentId = UUID.randomUUID();

        Exam exam1 = new Exam();
        exam1.setId(UUID.randomUUID());
        exam1.setTitle("Ocak Denemesi");
        exam1.setExamDate(LocalDate.of(2026, 1, 15));
        exam1.setStatus(Exam.ExamStatus.FINALIZED);

        Exam exam2 = new Exam();
        exam2.setId(UUID.randomUUID());
        exam2.setTitle("Şubat Denemesi");
        exam2.setExamDate(LocalDate.of(2026, 2, 20));
        exam2.setStatus(Exam.ExamStatus.FINALIZED);

        StudentExamResult result2 = new StudentExamResult();
        result2.setStudentId(studentId);
        result2.setExam(exam2);
        result2.setTotalScore(new BigDecimal("70.5"));
        result2.setPercentile(new BigDecimal("12.5"));
        result2.setStatus(StudentExamResult.ResultStatus.APPROVED);

        StudentExamResult result1 = new StudentExamResult();
        result1.setStudentId(studentId);
        result1.setExam(exam1);
        result1.setTotalScore(new BigDecimal("60.0"));
        result1.setPercentile(new BigDecimal("25.0"));
        result1.setStatus(StudentExamResult.ResultStatus.APPROVED);

        // Disordered insertion to mock repository
        when(studentExamResultRepository.findAll()).thenReturn(List.of(result2, result1));

        List<TrendDataPointDto> trends = studentTrendService.getOverallTrends(studentId);

        assertEquals(2, trends.size());
        assertEquals(exam1.getId(), trends.get(0).getExamId()); // Tarihe göre önce Ocak gelmeli
        assertEquals("Ocak Denemesi", trends.get(0).getExamName());
        assertEquals(new BigDecimal("60.0"), trends.get(0).getNetScore());
        
        assertEquals(exam2.getId(), trends.get(1).getExamId());
        assertEquals(new BigDecimal("70.5"), trends.get(1).getNetScore());
    }

    private Exam exam(String title, LocalDate date, Exam.ExamStatus status) {
        Exam e = new Exam();
        e.setId(UUID.randomUUID());
        e.setTitle(title);
        e.setExamDate(date);
        e.setStatus(status);
        return e;
    }

    private StudentExamResult result(UUID studentId, Exam exam,
                                     StudentExamResult.ResultStatus status, String score, String percentile) {
        StudentExamResult r = new StudentExamResult();
        r.setStudentId(studentId);
        r.setExam(exam);
        r.setStatus(status);
        r.setTotalScore(new BigDecimal(score));
        r.setPercentile(percentile == null ? null : new BigDecimal(percentile));
        return r;
    }

    @Test
    @DisplayName("getOverallTrends - yalnızca APPROVED + FINALIZED ve istenen öğrencinin sonuçlarını döndürür")
    void getOverallTrends_filtersOutNonApprovedAndNonFinalized() {
        UUID studentId = UUID.randomUUID();

        StudentExamResult ok = result(studentId,
                exam("A", LocalDate.of(2026, 1, 1), Exam.ExamStatus.FINALIZED),
                StudentExamResult.ResultStatus.APPROVED, "50.0", "10.0");
        StudentExamResult submitted = result(studentId,
                exam("B", LocalDate.of(2026, 2, 1), Exam.ExamStatus.FINALIZED),
                StudentExamResult.ResultStatus.SUBMITTED, "60.0", "5.0");
        StudentExamResult open = result(studentId,
                exam("C", LocalDate.of(2026, 3, 1), Exam.ExamStatus.OPEN),
                StudentExamResult.ResultStatus.APPROVED, "70.0", "1.0");
        StudentExamResult otherStudent = result(UUID.randomUUID(),
                exam("D", LocalDate.of(2026, 4, 1), Exam.ExamStatus.FINALIZED),
                StudentExamResult.ResultStatus.APPROVED, "80.0", "1.0");

        when(studentExamResultRepository.findAll())
                .thenReturn(List.of(ok, submitted, open, otherStudent));

        List<TrendDataPointDto> trends = studentTrendService.getOverallTrends(studentId);

        assertEquals(1, trends.size());
        assertEquals(ok.getExam().getId(), trends.get(0).getExamId());
        assertEquals(new BigDecimal("50.0"), trends.get(0).getNetScore());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("getSubjectTrends - JDBC satırlarını DTO'ya map eder ve percentile null bırakır")
    void getSubjectTrends_mapsRowsToDtos() throws Exception {
        UUID studentId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("id")).thenReturn(examId.toString());
        when(rs.getString("title")).thenReturn("Matematik Denemesi");
        when(rs.getDate("exam_date")).thenReturn(Date.valueOf(LocalDate.of(2026, 3, 10)));
        when(rs.getBigDecimal("net_score")).thenReturn(new BigDecimal("18.50"));

        when(jdbcTemplate.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<TrendDataPointDto> mapper = invocation.getArgument(2);
                    return List.of(mapper.mapRow(rs, 0));
                });

        List<TrendDataPointDto> trends = studentTrendService.getSubjectTrends(studentId, subjectId);

        assertEquals(1, trends.size());
        assertEquals(examId, trends.get(0).getExamId());
        assertEquals("Matematik Denemesi", trends.get(0).getExamName());
        assertEquals(LocalDate.of(2026, 3, 10), trends.get(0).getExamDate());
        assertEquals(new BigDecimal("18.50"), trends.get(0).getNetScore());
        assertNull(trends.get(0).getPercentile());
    }
}
