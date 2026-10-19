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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentTrendServiceTest {

    @Mock
    private StudentExamResultRepository studentExamResultRepository;

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
}
