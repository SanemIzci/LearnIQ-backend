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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private Exam exam(String title, LocalDate date, Exam.ExamStatus status) {
        Exam e = new Exam();
        e.setId(UUID.randomUUID());
        e.setTitle(title);
        e.setExamDate(date);
        e.setStatus(status);
        return e;
    }

    private StudentExamResult result(UUID studentId, Exam exam, StudentExamResult.ResultStatus status) {
        StudentExamResult r = new StudentExamResult();
        r.setStudentId(studentId);
        r.setExam(exam);
        r.setStatus(status);
        r.setTotalScore(new BigDecimal("80.0"));
        r.setPercentile(new BigDecimal("10.0"));
        return r;
    }

    private StudentBadge badge(String code, String title) {
        StudentBadge sb = new StudentBadge();
        sb.setBadge(new Badge(code, title, "Açıklama"));
        sb.setAwardedAt(LocalDateTime.now());
        return sb;
    }

    @Test
    @DisplayName("Kullanıcı bulunamazsa varsayılan isim/sınıf, sınav yok, rozet listesi boş")
    void getStudentHomeData_userMissing_usesDefaults() {
        UUID studentId = UUID.randomUUID();

        when(jdbcTemplate.queryForList(anyString(), any(Map.class))).thenReturn(List.of());
        when(studentExamResultRepository.findAll()).thenReturn(List.of());
        when(studentBadgeRepository.findLatestBadgesByStudentId(studentId)).thenReturn(List.of());

        StudentHomeResponseDto result = studentHomeService.getStudentHomeData(studentId);

        assertEquals("Öğrenci", result.getStudentName());
        assertEquals("Atanmamış", result.getClassroomName());
        assertNull(result.getLatestExam());
        assertTrue(result.getRecentBadges().isEmpty());
    }

    @Test
    @DisplayName("En güncel FINALIZED + APPROVED sınavı seçilir; açık/onaysız sonuçlar yok sayılır")
    void getStudentHomeData_picksMostRecentFinalizedApprovedExam() {
        UUID studentId = UUID.randomUUID();

        when(jdbcTemplate.queryForList(anyString(), any(Map.class)))
                .thenReturn(List.of(Map.of("full_name", "Ali Veli", "classroom_name", "12-A")));

        StudentExamResult older = result(studentId,
                exam("Eski", LocalDate.of(2026, 1, 1), Exam.ExamStatus.FINALIZED),
                StudentExamResult.ResultStatus.APPROVED);
        StudentExamResult newest = result(studentId,
                exam("Yeni", LocalDate.of(2026, 5, 1), Exam.ExamStatus.FINALIZED),
                StudentExamResult.ResultStatus.APPROVED);
        StudentExamResult openExam = result(studentId,
                exam("Açık", LocalDate.of(2026, 6, 1), Exam.ExamStatus.OPEN),
                StudentExamResult.ResultStatus.APPROVED);
        StudentExamResult submitted = result(studentId,
                exam("Onaysız", LocalDate.of(2026, 7, 1), Exam.ExamStatus.FINALIZED),
                StudentExamResult.ResultStatus.SUBMITTED);

        when(studentExamResultRepository.findAll()).thenReturn(List.of(older, newest, openExam, submitted));
        when(studentBadgeRepository.findLatestBadgesByStudentId(studentId)).thenReturn(List.of());

        StudentHomeResponseDto result = studentHomeService.getStudentHomeData(studentId);

        assertNotNull(result.getLatestExam());
        assertEquals("Yeni", result.getLatestExam().getTitle());
    }

    @Test
    @DisplayName("Rozet listesi en fazla 5 ile sınırlanır")
    void getStudentHomeData_limitsBadgesToFive() {
        UUID studentId = UUID.randomUUID();

        when(jdbcTemplate.queryForList(anyString(), any(Map.class)))
                .thenReturn(List.of(Map.of("full_name", "Ali Veli", "classroom_name", "12-A")));
        when(studentExamResultRepository.findAll()).thenReturn(List.of());
        when(studentBadgeRepository.findLatestBadgesByStudentId(studentId)).thenReturn(List.of(
                badge("B1", "Bir"), badge("B2", "İki"), badge("B3", "Üç"),
                badge("B4", "Dört"), badge("B5", "Beş"), badge("B6", "Altı")));

        StudentHomeResponseDto result = studentHomeService.getStudentHomeData(studentId);

        assertEquals(5, result.getRecentBadges().size());
    }
}
