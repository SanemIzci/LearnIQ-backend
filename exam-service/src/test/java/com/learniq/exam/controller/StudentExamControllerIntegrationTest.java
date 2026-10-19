package com.learniq.exam.controller;

import com.learniq.exam.dto.StudentDashboardResponse;
import com.learniq.exam.dto.TopStudentDto;
import com.learniq.exam.service.StudentDashboardService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = StudentExamController.class,
    excludeAutoConfiguration = {
        KafkaAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
    }
)
class StudentExamControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private com.learniq.exam.service.ExamService examService;
    @MockBean private com.learniq.exam.service.ExamResultService examResultService;
    @MockBean private com.learniq.exam.service.SubjectService subjectService;
    @MockBean private com.learniq.exam.service.AnalyticsService analyticsService;
    @MockBean private org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;
    @MockBean private javax.sql.DataSource dataSource;
    @MockBean private com.learniq.exam.repository.StudentExamResultRepository studentExamResultRepository;
    @MockBean private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @MockBean private org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @MockBean private com.learniq.exam.security.AccessControlService accessControlService;
    @MockBean private com.learniq.exam.repository.ExamRepository examRepository;
    @MockBean private com.learniq.exam.repository.ExamTypeSubjectRuleRepository ruleRepository;
    @MockBean private StudentDashboardService studentDashboardService;
    @MockBean private com.learniq.exam.service.AiReportService aiReportService;
    @MockBean private com.learniq.exam.repository.AiReportRepository aiReportRepository;
    @MockBean private com.learniq.exam.repository.StudentSubjectResultRepository studentSubjectResultRepository;
    @MockBean private com.learniq.exam.service.DataFingerprintService fingerprintService;
    @MockBean private com.learniq.exam.service.StudentHomeService studentHomeService;
    @MockBean private com.learniq.exam.service.BadgeService badgeService;
    @MockBean private com.learniq.exam.repository.BadgeRepository badgeRepository;
    @MockBean private com.learniq.exam.repository.StudentBadgeRepository studentBadgeRepository;

    @Test
    @DisplayName("GET /api/v1/students/me/exams/{examId}/dashboard - 200 OK")
    void getExamDashboard_success() throws Exception {
        UUID examId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        StudentDashboardResponse response = StudentDashboardResponse.builder()
                .classRank(1)
                .schoolRank(2)
                .netScore(new BigDecimal("90.5"))
                .percentile(new BigDecimal("10.0"))
                .classAverage(new BigDecimal("70.0"))
                .top3Students(List.of(
                        TopStudentDto.builder().rank(1).displayName("Sanem A.").netScore(new BigDecimal("90.5")).build()
                ))
                .build();

        when(studentDashboardService.getDashboard(examId, studentId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/students/me/exams/{examId}/dashboard", examId)
                .header("X-User-Id", studentId.toString())
                .header("X-User-Role", "STUDENT")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classRank").value(1))
                .andExpect(jsonPath("$.top3Students[0].displayName").value("Sanem A."));
    }

    @Test
    @DisplayName("GET /api/v1/students/me/exams/{examId}/dashboard - 403 Forbidden")
    void getExamDashboard_wrongRole_returns403() throws Exception {
        UUID examId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/students/me/exams/{examId}/dashboard", examId)
                .header("X-User-Id", teacherId.toString())
                .header("X-User-Role", "TEACHER") // Not STUDENT
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/students/me/exams/{examId}/ai-report - 200 OK")
    void requestAiReport_success() throws Exception {
        UUID examId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        com.learniq.exam.dto.AiReportResponseDto mockResponse = com.learniq.exam.dto.AiReportResponseDto.builder()
                .status("PENDING")
                .build();

        when(aiReportService.requestReport(examId, studentId)).thenReturn(mockResponse);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/students/me/exams/{examId}/ai-report", examId)
                .header("X-User-Id", studentId.toString())
                .header("X-User-Role", "STUDENT")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }
}
