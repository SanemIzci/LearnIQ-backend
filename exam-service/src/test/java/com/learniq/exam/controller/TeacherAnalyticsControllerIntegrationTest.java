package com.learniq.exam.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.exam.dto.ClassroomAnalyticsResponse;
import com.learniq.exam.dto.ClassroomComparisonItemDto;
import com.learniq.exam.dto.ClassroomComparisonResponse;
import com.learniq.exam.dto.ExamSummaryDto;
import com.learniq.exam.service.TeacherAnalyticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = TeacherAnalyticsController.class,
    excludeAutoConfiguration = {
        KafkaAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
    }
)
@DisplayName("TeacherAnalyticsController Integration Tests")
class TeacherAnalyticsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean private TeacherAnalyticsService teacherAnalyticsService;

    // Standard context mock beans to satisfy component scans
    @MockBean private com.learniq.exam.service.ExamService examService;
    @MockBean private com.learniq.exam.service.ExamResultService examResultService;
    @MockBean private com.learniq.exam.service.SubjectService subjectService;
    @MockBean private com.learniq.exam.service.SubjectTopicService subjectTopicService;
    @MockBean private com.learniq.exam.service.AnalyticsService analyticsService;
    @MockBean private org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;
    @MockBean private javax.sql.DataSource dataSource;
    @MockBean private com.learniq.exam.repository.StudentExamResultRepository studentExamResultRepository;
    @MockBean private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @MockBean private org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @MockBean private com.learniq.exam.security.AccessControlService accessControlService;
    @MockBean private com.learniq.exam.repository.ExamRepository examRepository;
    @MockBean private com.learniq.exam.repository.ExamTypeSubjectRuleRepository ruleRepository;
    @MockBean private com.learniq.exam.service.StudentDashboardService studentDashboardService;
    @MockBean private com.learniq.exam.service.AiReportService aiReportService;
    @MockBean private com.learniq.exam.repository.AiReportRepository aiReportRepository;
    @MockBean private com.learniq.exam.repository.StudentSubjectResultRepository studentSubjectResultRepository;
    @MockBean private com.learniq.exam.service.DataFingerprintService fingerprintService;
    @MockBean private com.learniq.exam.service.StudentHomeService studentHomeService;
    @MockBean private com.learniq.exam.service.BadgeService badgeService;
    @MockBean private com.learniq.exam.repository.BadgeRepository badgeRepository;
    @MockBean private com.learniq.exam.repository.StudentBadgeRepository studentBadgeRepository;
    @MockBean private com.learniq.exam.service.TeacherObservationService teacherObservationService;
    @MockBean private com.learniq.exam.service.ClassSubjectTrendCalculator classSubjectTrendCalculator;

    @Test
    @DisplayName("GET /api/v1/teachers/me/classrooms/{id}/analytics - 200 OK")
    void getClassroomAnalytics_success() throws Exception {
        UUID classroomId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID examId = UUID.randomUUID();

        ClassroomAnalyticsResponse mockResponse = ClassroomAnalyticsResponse.builder()
                .classroomId(classroomId)
                .classroomName("11-B")
                .totalStudents(25)
                .participationCount(24)
                .participationRate(new BigDecimal("96.00"))
                .classAverageScore(new BigDecimal("345.50"))
                .analyzedExam(ExamSummaryDto.builder()
                        .id(examId)
                        .title("TYT Deneme #2")
                        .examDate(LocalDate.now())
                        .examType("TYT")
                        .build())
                .subjectAverages(List.of())
                .recentExamTrends(List.of())
                .build();

        when(teacherAnalyticsService.getClassroomAnalytics(
                eq("tenant_alpha"), eq(teacherId), eq("TEACHER"), eq(classroomId), any()))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/teachers/me/classrooms/{classroomId}/analytics", classroomId)
                .header("X-Tenant-Id", "tenant_alpha")
                .header("X-User-Role", "TEACHER")
                .header("X-User-Id", teacherId.toString())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classroomName").value("11-B"))
                .andExpect(jsonPath("$.totalStudents").value(25))
                .andExpect(jsonPath("$.participationCount").value(24))
                .andExpect(jsonPath("$.participationRate").value(96.00))
                .andExpect(jsonPath("$.classAverageScore").value(345.50));
    }

    @Test
    @DisplayName("GET /api/v1/teachers/me/comparison - 200 OK")
    void compareClassrooms_success() throws Exception {
        UUID teacherId = UUID.randomUUID();
        UUID classroomA = UUID.randomUUID();
        UUID classroomB = UUID.randomUUID();
        UUID examId = UUID.randomUUID();

        ClassroomComparisonResponse mockResponse = ClassroomComparisonResponse.builder()
                .examId(examId)
                .examTitle("Genel TYT")
                .examDate(LocalDate.now())
                .classrooms(List.of(
                        ClassroomComparisonItemDto.builder()
                                .classroomId(classroomA)
                                .classroomName("12-A")
                                .totalStudents(30)
                                .participationCount(28)
                                .avgScore(new BigDecimal("360.00"))
                                .subjectNets(List.of())
                                .build(),
                        ClassroomComparisonItemDto.builder()
                                .classroomId(classroomB)
                                .classroomName("12-B")
                                .totalStudents(28)
                                .participationCount(26)
                                .avgScore(new BigDecimal("342.00"))
                                .subjectNets(List.of())
                                .build()
                ))
                .build();

        when(teacherAnalyticsService.compareClassrooms(
                eq("tenant_alpha"), eq(teacherId), eq("TEACHER"), any(), any()))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/teachers/me/comparison")
                .header("X-Tenant-Id", "tenant_alpha")
                .header("X-User-Role", "TEACHER")
                .header("X-User-Id", teacherId.toString())
                .param("classroomIds", classroomA.toString() + "," + classroomB.toString())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.examTitle").value("Genel TYT"))
                .andExpect(jsonPath("$.classrooms").isArray())
                .andExpect(jsonPath("$.classrooms[0].classroomName").value("12-A"))
                .andExpect(jsonPath("$.classrooms[1].classroomName").value("12-B"));
    }
}
