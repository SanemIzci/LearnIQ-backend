package com.learniq.exam.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = OpenApiController.class,
    excludeAutoConfiguration = {
        KafkaAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
    }
)
class OpenApiControllerTest {

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
    @MockBean private com.learniq.exam.service.ShareLinkService shareLinkService;
    @MockBean private com.learniq.exam.service.AiReportService aiReportService;
    @MockBean private com.learniq.exam.repository.AiReportRepository aiReportRepository;
    @MockBean private com.learniq.exam.repository.StudentSubjectResultRepository studentSubjectResultRepository;
    @MockBean private com.learniq.exam.service.DataFingerprintService fingerprintService;
    @MockBean private com.learniq.exam.service.StudentTrendService studentTrendService;
    @MockBean private com.learniq.exam.service.StudentHomeService studentHomeService;
    @MockBean private com.learniq.exam.service.BadgeService badgeService;
    @MockBean private com.learniq.exam.repository.BadgeRepository badgeRepository;
    @MockBean private com.learniq.exam.repository.StudentBadgeRepository studentBadgeRepository;
    @MockBean private com.learniq.exam.service.TeacherObservationService teacherObservationService;
    @MockBean private com.learniq.exam.service.ClassSubjectTrendCalculator classSubjectTrendCalculator;
    @MockBean private com.learniq.exam.service.SubjectTopicService subjectTopicService;
    @MockBean private com.learniq.exam.service.TeacherAnalyticsService teacherAnalyticsService;

    @Test
    @DisplayName("GET /v3/api-docs returns 200 OK with YAML OpenAPI spec")
    void getOpenApiSpec_v3ApiDocs_returnsYaml() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/yaml"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("openapi: 3.1.0")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("LearnIQ Platform API")));
    }

    @Test
    @DisplayName("GET /api-docs/openapi.yaml returns 200 OK with YAML OpenAPI spec")
    void getOpenApiSpec_yamlPath_returnsYaml() throws Exception {
        mockMvc.perform(get("/api-docs/openapi.yaml"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/yaml"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("openapi: 3.1.0")));
    }
}
