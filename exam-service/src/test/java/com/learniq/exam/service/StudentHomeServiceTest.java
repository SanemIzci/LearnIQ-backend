package com.learniq.exam.service;

import com.learniq.exam.domain.Badge;
import com.learniq.exam.domain.Exam;
import com.learniq.exam.domain.StudentBadge;
import com.learniq.exam.domain.StudentExamResult;
import com.learniq.exam.dto.StudentHomeResponseDto;
import com.learniq.exam.repository.StudentBadgeRepository;
import com.learniq.exam.repository.StudentExamResultRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentHomeServiceTest {

    @Mock private StudentExamResultRepository studentExamResultRepository;
    @Mock private StudentBadgeRepository studentBadgeRepository;
    @Mock private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private StudentHomeService studentHomeService;

    @Test
    @DisplayName("getStudentHomeData - Ana ekran verilerini başarıyla birleştirir")
    void getStudentHomeData_success() {
        UUID studentId = UUID.randomUUID();

        // Mock JDBC call
        when(jdbcTemplate.queryForList(anyString(), any(Map.class)))
                .thenReturn(List.of(Map.of(
                        "full_name", "Ayşe Kaya",
                        "classroom_name", "11-B"
                )));

        // Mock Latest Exam
        Exam exam = new Exam();
        exam.setId(UUID.randomUUID());
        exam.setTitle("Son Deneme");
        exam.setExamDate(LocalDate.now());
        exam.setStatus(Exam.ExamStatus.FINALIZED);

        StudentExamResult r = new StudentExamResult();
        r.setStudentId(studentId);
        r.setStatus(StudentExamResult.ResultStatus.APPROVED);
        r.setExam(exam);
        r.setTotalScore(new BigDecimal("80.0"));
        r.setPercentile(new BigDecimal("10.0"));

        when(studentExamResultRepository.findAll()).thenReturn(List.of(r));

        // Mock Badges
        Badge b = new Badge("TOP_3", "Derece", "Açıklama");
        StudentBadge sb = new StudentBadge();
        sb.setBadge(b);
        sb.setAwardedAt(LocalDateTime.now());
        when(studentBadgeRepository.findLatestBadgesByStudentId(studentId)).thenReturn(List.of(sb));

        StudentHomeResponseDto result = studentHomeService.getStudentHomeData(studentId);

        assertEquals("Ayşe Kaya", result.getStudentName());
        assertEquals("11-B", result.getClassroomName());
        assertNotNull(result.getLatestExam());
        assertEquals("Son Deneme", result.getLatestExam().getTitle());
        assertEquals(1, result.getRecentBadges().size());
        assertEquals("Derece", result.getRecentBadges().get(0).getTitle());
    }
}
