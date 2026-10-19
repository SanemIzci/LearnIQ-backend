package com.learniq.exam.controller;

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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = StudentProfileController.class,
    excludeAutoConfiguration = {
        KafkaAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
    }
)
class StudentProfileControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private com.learniq.exam.service.ExamService examService;
    @MockBean private com.learniq.exam.service.ExamResultService examResultService;
    @MockBean private com.learniq.exam.service.SubjectService subjectService;
    @MockBean private com.learniq.exam.service.AnalyticsService analyticsService;
    @MockBean private org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;
    @MockBean private javax.sql.DataSource dataSource;
    @MockBean private com.learniq.exam.repository.StudentExamResultRepository studentExamResultRepository;
    @MockBean private org.springframework.jdbc.core.JdbcTemplate jdbcTmpl;
    @MockBean private org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbcTemplate;
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

    @Test
    @DisplayName("PUT /api/v1/students/me/leaderboard-consent - 200 OK")
    void updateConsent_success() throws Exception {
        UUID studentId = UUID.randomUUID();

        when(jdbcTemplate.update(anyString(), any(java.util.Map.class))).thenReturn(1);

        mockMvc.perform(put("/api/v1/students/me/leaderboard-consent")
                .param("consent", "true")
                .header("X-User-Id", studentId.toString())
                .header("X-User-Role", "STUDENT")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/v1/students/me/leaderboard-consent - 404 Not Found (Kullanıcı Yok)")
    void updateConsent_userNotFound_returns404() throws Exception {
        UUID studentId = UUID.randomUUID();

        when(jdbcTemplate.update(anyString(), any(java.util.Map.class))).thenReturn(0);

        mockMvc.perform(put("/api/v1/students/me/leaderboard-consent")
                .param("consent", "true")
                .header("X-User-Id", studentId.toString())
                .header("X-User-Role", "STUDENT")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
