package com.learniq.exam.service;

import com.learniq.exam.domain.Exam;
import com.learniq.exam.domain.StudentExamResult;
import com.learniq.exam.dto.StudentDashboardResponse;
import com.learniq.exam.dto.TopStudentDto;
import com.learniq.exam.exception.EntityNotFoundException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentDashboardServiceTest {

    @Mock
    private StudentExamResultRepository studentExamResultRepository;

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private StudentDashboardService dashboardService;

    private static final UUID EXAM_ID = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID CLASSROOM_ID = UUID.randomUUID();

    private StudentExamResult result(Integer classRank, Integer schoolRank, String total, String percentile, boolean finalized) {
        Exam exam = new Exam();
        exam.setStatus(finalized ? Exam.ExamStatus.FINALIZED : Exam.ExamStatus.OPEN);

        StudentExamResult r = new StudentExamResult();
        r.setStudentId(STUDENT_ID);
        r.setExam(exam);
        r.setClassRank(classRank);
        r.setSchoolRank(schoolRank);
        r.setTotalScore(new BigDecimal(total));
        r.setPercentile(percentile == null ? null : new BigDecimal(percentile));
        return r;
    }

    private void stubClassroom(UUID classroomId) {
        when(jdbcTemplate.queryForList(anyString(), anyMap(), eq(UUID.class)))
                .thenReturn(classroomId == null ? List.of() : List.of(classroomId));
    }

    /**
     * rows: {consent(Boolean), fullName(String), classRank(Integer), totalScore(BigDecimal)}
     */
    private void stubTop3(BigDecimal classAverage, Object[]... rows) {
        when(jdbcTemplate.queryForObject(contains("AVG"), anyMap(), eq(BigDecimal.class)))
                .thenReturn(classAverage);
        when(jdbcTemplate.query(contains("LIMIT 3"), anyMap(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<TopStudentDto> mapper = invocation.getArgument(2);
                    List<TopStudentDto> out = new ArrayList<>();
                    int rowNum = 0;
                    for (Object[] row : rows) {
                        ResultSet rs = mock(ResultSet.class);
                        when(rs.getBoolean("leaderboard_consent")).thenReturn((Boolean) row[0]);
                        when(rs.getString("full_name")).thenReturn((String) row[1]);
                        when(rs.getInt("class_rank")).thenReturn((Integer) row[2]);
                        when(rs.getBigDecimal("total_score")).thenReturn((BigDecimal) row[3]);
                        out.add(mapper.mapRow(rs, rowNum++));
                    }
                    return out;
                });
    }

    @Test
    @DisplayName("Başarılı senaryo: sıra, ortalama yuvarlama ve KVKK rızasına göre isim formatlama")
    void getDashboard_success_formatsNamesWithConsent() {
        when(studentExamResultRepository.findByExamIdAndStudentId(EXAM_ID, STUDENT_ID))
                .thenReturn(Optional.of(result(2, 5, "350.00", "15.50", true)));
        stubClassroom(CLASSROOM_ID);
        stubTop3(new BigDecimal("310.555"),
                new Object[]{true, "Ayşe Yılmaz", 1, new BigDecimal("400.00")},
                new Object[]{false, "Ahmet Demir", 2, new BigDecimal("350.00")});

        StudentDashboardResponse response = dashboardService.getDashboard(EXAM_ID, STUDENT_ID);

        assertEquals(2, response.getClassRank());
        assertEquals(5, response.getSchoolRank());
        assertEquals(new BigDecimal("310.56"), response.getClassAverage());

        List<TopStudentDto> top3 = response.getTop3Students();
        assertEquals(2, top3.size());
        assertEquals("Ayşe Y.", top3.get(0).getDisplayName());
        assertEquals("Anonim Öğrenci", top3.get(1).getDisplayName());
    }

    @Test
    @DisplayName("Tek kelimelik isim: 'A***' olarak maskelenir")
    void getDashboard_singleWordName_isMasked() {
        when(studentExamResultRepository.findByExamIdAndStudentId(EXAM_ID, STUDENT_ID))
                .thenReturn(Optional.of(result(1, 1, "400.00", "1.00", true)));
        stubClassroom(CLASSROOM_ID);
        stubTop3(new BigDecimal("400.00"),
                new Object[]{true, "Ayşe", 1, new BigDecimal("400.00")});

        StudentDashboardResponse response = dashboardService.getDashboard(EXAM_ID, STUDENT_ID);

        assertEquals("A***", response.getTop3Students().get(0).getDisplayName());
    }

    @Test
    @DisplayName("Rıza yoksa isim 'Anonim Öğrenci' olur")
    void getDashboard_noConsent_returnsAnonymous() {
        when(studentExamResultRepository.findByExamIdAndStudentId(EXAM_ID, STUDENT_ID))
                .thenReturn(Optional.of(result(1, 1, "400.00", "1.00", true)));
        stubClassroom(CLASSROOM_ID);
        stubTop3(new BigDecimal("400.00"),
                new Object[]{false, "Ayşe Yılmaz", 1, new BigDecimal("400.00")});

        StudentDashboardResponse response = dashboardService.getDashboard(EXAM_ID, STUDENT_ID);

        assertEquals("Anonim Öğrenci", response.getTop3Students().get(0).getDisplayName());
    }

    @Test
    @DisplayName("percentile null ise NPE olmadan null döner")
    void getDashboard_nullPercentile_isPreserved() {
        when(studentExamResultRepository.findByExamIdAndStudentId(EXAM_ID, STUDENT_ID))
                .thenReturn(Optional.of(result(3, 9, "250.00", null, true)));
        stubClassroom(CLASSROOM_ID);
        stubTop3(new BigDecimal("250.00"));

        StudentDashboardResponse response = dashboardService.getDashboard(EXAM_ID, STUDENT_ID);

        assertNull(response.getPercentile());
        assertEquals(3, response.getClassRank());
    }

    @Test
    @DisplayName("Sınıf ortalaması null ise 0 olarak döner")
    void getDashboard_nullClassAverage_defaultsToZero() {
        when(studentExamResultRepository.findByExamIdAndStudentId(EXAM_ID, STUDENT_ID))
                .thenReturn(Optional.of(result(1, 1, "300.00", "10.00", true)));
        stubClassroom(CLASSROOM_ID);
        stubTop3(null);

        StudentDashboardResponse response = dashboardService.getDashboard(EXAM_ID, STUDENT_ID);

        assertEquals(BigDecimal.ZERO, response.getClassAverage());
    }

    @Test
    @DisplayName("Top 3 listesi boşsa boş liste döner")
    void getDashboard_emptyTop3_returnsEmptyList() {
        when(studentExamResultRepository.findByExamIdAndStudentId(EXAM_ID, STUDENT_ID))
                .thenReturn(Optional.of(result(1, 1, "300.00", "10.00", true)));
        stubClassroom(CLASSROOM_ID);
        stubTop3(new BigDecimal("300.00"));

        StudentDashboardResponse response = dashboardService.getDashboard(EXAM_ID, STUDENT_ID);

        assertTrue(response.getTop3Students().isEmpty());
    }

    @Test
    @DisplayName("Öğrencinin sınıfı yoksa IllegalStateException")
    void getDashboard_noClassroom_throws() {
        when(studentExamResultRepository.findByExamIdAndStudentId(EXAM_ID, STUDENT_ID))
                .thenReturn(Optional.of(result(1, 1, "300.00", "10.00", true)));
        stubClassroom(null);

        assertThrows(IllegalStateException.class, () -> dashboardService.getDashboard(EXAM_ID, STUDENT_ID));
    }

    @Test
    @DisplayName("Sınav FINALIZED değilse IllegalStateException")
    void getDashboard_examNotFinalized_throws() {
        when(studentExamResultRepository.findByExamIdAndStudentId(EXAM_ID, STUDENT_ID))
                .thenReturn(Optional.of(result(null, null, "300.00", null, false)));

        assertThrows(IllegalStateException.class, () -> dashboardService.getDashboard(EXAM_ID, STUDENT_ID));
    }

    @Test
    @DisplayName("Sonuç kaydı yoksa EntityNotFoundException")
    void getDashboard_resultNotFound_throws() {
        when(studentExamResultRepository.findByExamIdAndStudentId(EXAM_ID, STUDENT_ID))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> dashboardService.getDashboard(EXAM_ID, STUDENT_ID));
        verify(jdbcTemplate, never()).queryForList(anyString(), anyMap(), eq(UUID.class));
    }
}
