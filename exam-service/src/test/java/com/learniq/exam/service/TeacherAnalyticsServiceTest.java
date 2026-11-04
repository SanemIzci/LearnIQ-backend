package com.learniq.exam.service;

import com.learniq.common.tenant.TenantContext;
import com.learniq.exam.dto.ClassroomAnalyticsResponse;
import com.learniq.exam.dto.ClassroomComparisonResponse;
import com.learniq.exam.exception.EntityNotFoundException;
import com.learniq.exam.security.AccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeacherAnalyticsService Unit Tests")
class TeacherAnalyticsServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private AccessControlService accessControlService;

    @InjectMocks
    private TeacherAnalyticsService teacherAnalyticsService;

    private final String tenantId = "tenant_test";
    private final UUID teacherId = UUID.randomUUID();
    private final UUID classroomId = UUID.randomUUID();
    private final UUID examId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(tenantId);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("getClassroomAnalytics should throw exception when access is denied")
    void getClassroomAnalytics_accessDenied_throwsForbidden() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"))
                .when(accessControlService).validateClassroomAccess(tenantId, teacherId, "TEACHER", classroomId);

        assertThatThrownBy(() -> teacherAnalyticsService.getClassroomAnalytics(
                tenantId, teacherId, "TEACHER", classroomId, examId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Access denied");

        verify(jdbcTemplate, never()).queryForObject(anyString(), any(Class.class), any());
    }

    @Test
    @DisplayName("getClassroomAnalytics should throw EntityNotFoundException when classroom does not exist")
    void getClassroomAnalytics_classroomNotFound_throws404() {
        doNothing().when(accessControlService).validateClassroomAccess(tenantId, teacherId, "TEACHER", classroomId);
        when(jdbcTemplate.queryForObject(contains("SELECT name FROM tenant_test.classrooms"), eq(String.class), eq(classroomId)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThatThrownBy(() -> teacherAnalyticsService.getClassroomAnalytics(
                tenantId, teacherId, "TEACHER", classroomId, examId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Classroom not found");
    }

    @Test
    @DisplayName("getClassroomAnalytics should return metrics when exam and results exist")
    void getClassroomAnalytics_success() {
        doNothing().when(accessControlService).validateClassroomAccess(tenantId, teacherId, "TEACHER", classroomId);
        when(jdbcTemplate.queryForObject(contains("SELECT name FROM tenant_test.classrooms"), eq(String.class), eq(classroomId)))
                .thenReturn("12-A");
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM tenant_test.classroom_students"), eq(Integer.class), eq(classroomId)))
                .thenReturn(20);

        // Exam detail lookup
        when(jdbcTemplate.queryForObject(contains("SELECT id, title, exam_date, exam_type FROM tenant_test.exams"), any(RowMapper.class), eq(examId)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    var rs = mock(java.sql.ResultSet.class);
                    when(rs.getString("id")).thenReturn(examId.toString());
                    when(rs.getString("title")).thenReturn("TYT Deneme 1");
                    when(rs.getDate("exam_date")).thenReturn(java.sql.Date.valueOf("2026-10-15"));
                    when(rs.getString("exam_type")).thenReturn("TYT");
                    return mapper.mapRow(rs, 1);
                });

        // Exam stats
        Map<String, Object> stats = new HashMap<>();
        stats.put("part_count", 18);
        stats.put("avg_score", 350.50);
        when(jdbcTemplate.queryForMap(contains("count(ser.id) as part_count"), eq(classroomId), eq(examId)))
                .thenReturn(stats);

        // Subject averages
        when(jdbcTemplate.query(contains("AVG(ssr.net_score) as avg_net"), any(RowMapper.class), eq(classroomId), eq(examId)))
                .thenReturn(List.of());

        // Recent trends
        when(jdbcTemplate.query(contains("SELECT e.id, e.title, e.exam_date"), any(RowMapper.class), eq(classroomId)))
                .thenReturn(List.of());

        ClassroomAnalyticsResponse response = teacherAnalyticsService.getClassroomAnalytics(
                tenantId, teacherId, "TEACHER", classroomId, examId);

        assertThat(response.getClassroomId()).isEqualTo(classroomId);
        assertThat(response.getClassroomName()).isEqualTo("12-A");
        assertThat(response.getTotalStudents()).isEqualTo(20);
        assertThat(response.getParticipationCount()).isEqualTo(18);
        assertThat(response.getParticipationRate()).isEqualTo(new BigDecimal("90.00"));
        assertThat(response.getClassAverageScore()).isEqualTo(new BigDecimal("350.50"));
    }

    @Test
    @DisplayName("compareClassrooms should throw IllegalArgumentException when less than 2 classrooms provided")
    void compareClassrooms_lessThanTwoClassrooms_throwsException() {
        assertThatThrownBy(() -> teacherAnalyticsService.compareClassrooms(
                tenantId, teacherId, "TEACHER", List.of(classroomId), examId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least two classrooms");
    }

    @Test
    @DisplayName("compareClassrooms should return comparisons for valid classrooms and exam")
    void compareClassrooms_success() {
        UUID classroomB = UUID.randomUUID();
        List<UUID> classroomIds = List.of(classroomId, classroomB);

        doNothing().when(accessControlService).validateClassroomAccess(anyString(), any(), anyString(), any());

        // Exam metadata
        when(jdbcTemplate.queryForObject(contains("SELECT id, title, exam_date, exam_type FROM tenant_test.exams"), any(RowMapper.class), eq(examId)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    var rs = mock(java.sql.ResultSet.class);
                    when(rs.getString("id")).thenReturn(examId.toString());
                    when(rs.getString("title")).thenReturn("AYT Deneme 1");
                    when(rs.getDate("exam_date")).thenReturn(java.sql.Date.valueOf("2026-10-20"));
                    when(rs.getString("exam_type")).thenReturn("AYT");
                    return mapper.mapRow(rs, 1);
                });

        // Classroom 1 queries
        when(jdbcTemplate.queryForObject(contains("SELECT name FROM tenant_test.classrooms"), eq(String.class), eq(classroomId)))
                .thenReturn("12-A");
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM tenant_test.classroom_students"), eq(Integer.class), eq(classroomId)))
                .thenReturn(25);
        Map<String, Object> statsA = Map.of("part_count", 20, "avg_score", 320.0);
        when(jdbcTemplate.queryForMap(contains("count(ser.id) as part_count"), eq(classroomId), eq(examId)))
                .thenReturn(statsA);

        // Classroom 2 queries
        when(jdbcTemplate.queryForObject(contains("SELECT name FROM tenant_test.classrooms"), eq(String.class), eq(classroomB)))
                .thenReturn("12-B");
        when(jdbcTemplate.queryForObject(contains("SELECT count(*) FROM tenant_test.classroom_students"), eq(Integer.class), eq(classroomB)))
                .thenReturn(24);
        Map<String, Object> statsB = Map.of("part_count", 22, "avg_score", 340.0);
        when(jdbcTemplate.queryForMap(contains("count(ser.id) as part_count"), eq(classroomB), eq(examId)))
                .thenReturn(statsB);

        when(jdbcTemplate.query(contains("AVG(ssr.net_score) as avg_net"), any(RowMapper.class), any(), eq(examId)))
                .thenReturn(List.of());

        ClassroomComparisonResponse response = teacherAnalyticsService.compareClassrooms(
                tenantId, teacherId, "TEACHER", classroomIds, examId);

        assertThat(response.getExamId()).isEqualTo(examId);
        assertThat(response.getExamTitle()).isEqualTo("AYT Deneme 1");
        assertThat(response.getClassrooms()).hasSize(2);
        assertThat(response.getClassrooms().get(0).getClassroomName()).isEqualTo("12-A");
        assertThat(response.getClassrooms().get(1).getClassroomName()).isEqualTo("12-B");
    }
}
