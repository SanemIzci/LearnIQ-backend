package com.learniq.exam.service;

import com.learniq.exam.dto.AdminDashboardResponse;
import com.learniq.exam.dto.ClassSubjectTrendRow;
import com.learniq.exam.dto.ClassroomAdminSummary;
import com.learniq.exam.dto.RecentObservationDto;
import com.learniq.exam.dto.SubjectScorecardResponse;
import com.learniq.exam.dto.SubjectScorecardRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAnalyticsService Unit Tests (şema-nitelikli)")
class AdminAnalyticsServiceTest {

    private static final String TENANT = "akademi_1";
    private static final UUID CLASSROOM_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID EXAM_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final UUID SUBJECT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    private static final UUID LATEST_EXAM_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private AdminAnalyticsService service;

    @SuppressWarnings("unchecked")
    private <T> void stubQuery(String sqlMatcher, List<T> result) {
        when(jdbcTemplate.query(contains(sqlMatcher), any(RowMapper.class)))
                .thenReturn(result);
    }

    @Test
    @DisplayName("getDashboard - tenant şeması ile nitelikli tek sorgu (class_subject_trends)")
    void dashboard_schemaQualifiedSql() {
        stubQuery(".class_subject_trends", List.of(mock(ClassroomAdminSummary.class)));

        AdminDashboardResponse resp = service.getDashboard(TENANT);

        assertThat(resp.getClassroomCount()).isEqualTo(1);
        verify(jdbcTemplate).query(contains(TENANT + ".classrooms"), any(RowMapper.class));
        verify(jdbcTemplate).query(contains(TENANT + ".exams"), any(RowMapper.class));
        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    @DisplayName("getDashboard - geçersiz şema → IllegalStateException mesajı, sorgu YOK")
    void dashboard_invalidTenant_rejected() {
        assertThatThrownBy(() -> service.getDashboard("Puplic; DROP SCHEMA public"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("getSubjectScorecard - examId verilince tek sorgu, lookup YOK")
    void scorecard_explicitExamId() {
        when(jdbcTemplate.query(contains(".class_subject_trends"), any(RowMapper.class), eq(EXAM_ID)))
                .thenReturn(List.of(SubjectScorecardRow.builder()
                        .subjectId(SUBJECT_ID).subjectName("Matematik")
                        .classroomCount(2).avgNet(new BigDecimal("15.50"))
                        .minNet(new BigDecimal("14.00")).maxNet(new BigDecimal("17.00"))
                        .bestClassroomName("9A").worstClassroomName("9B").build()));

        SubjectScorecardResponse resp = service.getSubjectScorecard(TENANT, EXAM_ID);

        assertThat(resp.getExamId()).isEqualTo(EXAM_ID);
        assertThat(resp.getSubjects()).hasSize(1);
        assertThat(resp.getSubjects()).allSatisfy(r -> assertThat(r.getAvgNet()).isEqualByComparingTo("15.50"));
        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(UUID.class));
    }

    @Test
    @DisplayName("getSubjectScorecard - examId null → tenant'ın son deneme lookup'u yapılır")
    void scorecard_nullExamId_resolvesLatestExam() {
        when(jdbcTemplate.queryForObject(contains("ORDER BY e.exam_date DESC"), eq(UUID.class)))
                .thenReturn(LATEST_EXAM_ID);
        when(jdbcTemplate.query(contains(".subject_id"), any(RowMapper.class), eq(LATEST_EXAM_ID)))
                .thenReturn(List.of(SubjectScorecardRow.builder().subjectId(SUBJECT_ID).build()));

        SubjectScorecardResponse resp = service.getSubjectScorecard(TENANT, null);

        assertThat(resp.getExamId()).isEqualTo(LATEST_EXAM_ID);
        verify(jdbcTemplate).queryForObject(contains(TENANT + ".class_subject_trends"), eq(UUID.class));
    }

    @Test
    @DisplayName("getSubjectScorecard - tenant'ta hiç deneme yoksa boş skorbordu")
    void scorecard_noExams_emptyResponse() {
        when(jdbcTemplate.queryForObject(anyString(), eq(UUID.class)))
                .thenThrow(new EmptyResultDataAccessException(1));

        SubjectScorecardResponse resp = service.getSubjectScorecard(TENANT, null);

        assertThat(resp.getExamId()).isNull();
        assertThat(resp.getSubjects()).isEmpty();
        verify(jdbcTemplate, never()).query(contains(".subjects"), any(RowMapper.class), any(UUID.class));
    }

    @Test
    @DisplayName("getTrends - subjectId null → sınırsız sorgu + limit parametresi")
    void trends_defaultLimit() {
        when(jdbcTemplate.query(contains("delta_from_previous"), any(RowMapper.class), eq(50)))
                .thenReturn(List.of(ClassSubjectTrendRow.builder()
                        .classroomName("9A").subjectName("Matematik")
                        .examTitle("Deneme 1").examDate(LocalDate.of(2026, 10, 1))
                        .avgNet(new BigDecimal("15.00"))
                        .deltaFromPrevious(new BigDecimal("1.25"))
                        .trendDirection("UP").build()));

        List<ClassSubjectTrendRow> rows = service.getTrends(TENANT, null, 50);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getTrendDirection()).isEqualTo("UP");
        verify(jdbcTemplate).query(contains(TENANT + ".classrooms"), any(RowMapper.class), eq(50));
    }

    @Test
    @DisplayName("getTrends - subjectId filtresi → WHERE + (subjectId, limit) argümanları")
    void trends_subjectFiltered() {
        when(jdbcTemplate.query(contains("WHERE t.subject_id = ?"), any(RowMapper.class), eq(SUBJECT_ID), eq(10)))
                .thenReturn(List.of());

        assertThat(service.getTrends(TENANT, SUBJECT_ID, 10)).isEmpty();

        verify(jdbcTemplate).query(contains("WHERE t.subject_id = ?"), any(RowMapper.class), eq(SUBJECT_ID), eq(10));
    }

    @Test
    @DisplayName("getRecentObservations - observations tablosu + limit parametresi")
    void recentObservations_schemaQualified() {
        when(jdbcTemplate.query(contains(".teacher_observations"), any(RowMapper.class), eq(10)))
                .thenReturn(List.of(RecentObservationDto.builder()
                        .classroomName("9A").subjectName("Fizik")
                        .content("Netler bölüme göre artıştan").observedAt(null).build()));

        List<RecentObservationDto> rows = service.getRecentObservations(TENANT, 10);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getClassroomName()).isEqualTo("9A");
        verify(jdbcTemplate).query(contains(TENANT + ".teacher_observations"), any(RowMapper.class), eq(10));
    }
}
