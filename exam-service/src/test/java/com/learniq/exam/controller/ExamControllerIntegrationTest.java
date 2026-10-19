package com.learniq.exam.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.exam.dto.ExamCreateRequest;
import com.learniq.exam.dto.ExamResponse;
import com.learniq.exam.dto.ExamResultSubmitRequest;
import com.learniq.exam.dto.SubjectInputDto;
import com.learniq.exam.service.AnalyticsService;
import com.learniq.exam.service.ExamResultService;
import com.learniq.exam.service.ExamService;
import com.learniq.exam.service.SubjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = {
        ExamController.class,
        ExamResultController.class
    },
    excludeAutoConfiguration = {
        KafkaAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
    },
    excludeFilters = {
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = {
                com.learniq.common.tenant.TenantInterceptorConfig.class,
                com.learniq.exam.config.S3Config.class,
                com.learniq.common.tenant.hibernate.HibernateMultiTenancyConfig.class,
                com.learniq.common.tenant.hibernate.SchemaMultiTenantConnectionProvider.class
            }
        )
    }
)
@DisplayName("Exam Controller Integration Tests")
class ExamControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean private ExamService examService;
    @MockBean private ExamResultService examResultService;
    @MockBean private SubjectService subjectService;
    @MockBean private AnalyticsService analyticsService;
    @MockBean private KafkaTemplate<String, Object> kafkaTemplate;
    @MockBean private DataSource dataSource;
    @MockBean private com.learniq.exam.repository.StudentExamResultRepository studentExamResultRepository;
    @MockBean private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @MockBean private com.learniq.exam.security.AccessControlService accessControlService;
    @MockBean private com.learniq.exam.repository.ExamRepository examRepository;
    @MockBean private com.learniq.exam.repository.ExamTypeSubjectRuleRepository ruleRepository;
    @MockBean private com.learniq.exam.service.StudentDashboardService studentDashboardService;
    @MockBean private org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @MockBean private com.learniq.exam.service.AiReportService aiReportService;
    @MockBean private com.learniq.exam.repository.AiReportRepository aiReportRepository;
    @MockBean private com.learniq.exam.repository.StudentSubjectResultRepository studentSubjectResultRepository;
    @MockBean private com.learniq.exam.service.DataFingerprintService fingerprintService;
    @MockBean private com.learniq.exam.service.ShareLinkService shareLinkService;
    @MockBean private com.learniq.exam.service.StudentHomeService studentHomeService;
    @MockBean private com.learniq.exam.service.BadgeService badgeService;
    @MockBean private com.learniq.exam.repository.BadgeRepository badgeRepository;
    @MockBean private com.learniq.exam.repository.StudentBadgeRepository studentBadgeRepository;

    private static final UUID EXAM_ID    = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID STUDENT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    private static final UUID SUBJECT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");

    @Test
    @DisplayName("POST /api/v1/exams: valid request should return 201 with exam response body")
    void createExam_validRequest_returns201() throws Exception {
        ExamCreateRequest request = new ExamCreateRequest();
        request.setTitle("Haziran TYT Denemesi");
        request.setExamDate(LocalDate.of(2025, 6, 15));
        request.setExamType("TYT");

        ExamResponse stubResponse = ExamResponse.builder()
                .id(EXAM_ID)
                .title("Haziran TYT Denemesi")
                .examDate(LocalDate.of(2025, 6, 15))
                .examType("TYT")
                .subjects(List.of())
                .createdAt(LocalDateTime.now())
                .build();

        when(examService.createExam(any())).thenReturn(stubResponse);

        mockMvc.perform(post("/api/v1/exams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(EXAM_ID.toString()));
    }

    @Test
    @DisplayName("POST /api/v1/exams/{id}/results/me: negative correctCount should return 400")
    void submitExamResult_negativeScore_returns400() throws Exception {
        SubjectInputDto subjectDto = new SubjectInputDto();
        subjectDto.setSubjectId(SUBJECT_ID);
        subjectDto.setCorrectCount(-10); // Negatif sayı validasyona takılır
        subjectDto.setWrongCount(2);

        ExamResultSubmitRequest badRequest = new ExamResultSubmitRequest();
        badRequest.setSubjects(List.of(subjectDto));

        mockMvc.perform(post("/api/v1/exams/{examId}/results/me", EXAM_ID)
                        .header("X-User-Id", STUDENT_ID.toString())
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("POST /api/v1/exams/{id}/results/me: empty subjectResults should return 400")
    void submitExamResult_emptySubjectList_returns400() throws Exception {
        ExamResultSubmitRequest badRequest = new ExamResultSubmitRequest();
        badRequest.setSubjects(List.of());

        mockMvc.perform(post("/api/v1/exams/{examId}/results/me", EXAM_ID)
                        .header("X-User-Id", STUDENT_ID.toString())
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
