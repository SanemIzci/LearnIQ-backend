package com.learniq.exam.controller;

import com.learniq.exam.dto.StudentDashboardResponse;
import com.learniq.exam.service.ShareLinkService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = PublicShareController.class,
    excludeAutoConfiguration = {
        KafkaAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
    }
)
class PublicShareControllerIntegrationTest {

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
    @MockBean private ShareLinkService shareLinkService;
    @MockBean private com.learniq.exam.service.AiReportService aiReportService;
    @MockBean private com.learniq.exam.repository.AiReportRepository aiReportRepository;
    @MockBean private com.learniq.exam.repository.StudentSubjectResultRepository studentSubjectResultRepository;
    @MockBean private com.learniq.exam.service.DataFingerprintService fingerprintService;
    @MockBean private com.learniq.exam.service.StudentTrendService studentTrendService;
    @MockBean private com.learniq.exam.service.StudentHomeService studentHomeService;
    @MockBean private com.learniq.exam.service.BadgeService badgeService;
    @MockBean private com.learniq.exam.repository.BadgeRepository badgeRepository;
    @MockBean private com.learniq.exam.repository.StudentBadgeRepository studentBadgeRepository;

    @Test
    @DisplayName("GET /api/v1/public/share/{token} - 200 OK")
    void getSharedSnapshot_success() throws Exception {
        String token = "valid-token-1234";

        StudentDashboardResponse mockResponse = StudentDashboardResponse.builder()
                .netScore(new BigDecimal("95.0"))
                .build();

        when(shareLinkService.getSharedSnapshot(token)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/public/share/{token}", token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netScore").value(95.0));
    }

    @Test
    @DisplayName("GET /api/v1/public/share/{token} - 400 Bad Request (Expired or Invalid)")
    void getSharedSnapshot_invalid_returns400() throws Exception {
        String token = "expired-token";

        when(shareLinkService.getSharedSnapshot(token)).thenThrow(new IllegalArgumentException("Expired"));

        mockMvc.perform(get("/api/v1/public/share/{token}", token)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
