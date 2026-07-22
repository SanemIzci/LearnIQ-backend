package com.learniq.exam.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.exam.controller.AnalyticsController;
import com.learniq.exam.controller.ExamController;
import com.learniq.exam.controller.ExamResultController;
import com.learniq.exam.controller.SubjectController;
import com.learniq.exam.dto.ExamCreateRequest;
import com.learniq.exam.dto.ExamResponse;
import com.learniq.exam.dto.StudentExamResultRequest;
import com.learniq.exam.dto.SubjectResultDto;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer integration tests for exam-service REST controllers.
 *
 * Uses @WebMvcTest to spin up ONLY the web layer:
 *   - Security, Kafka, and S3 autoconfiguration are excluded to keep the
 *     context lightweight and avoid needing running infrastructure.
 *   - Service and infrastructure beans are all replaced with @MockBean.
 */
@WebMvcTest(
    controllers = {
        ExamController.class,
        ExamResultController.class,
        SubjectController.class,
        AnalyticsController.class
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

    private static final UUID EXAM_ID    = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID STUDENT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    private static final UUID SUBJECT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");

    // ── Test #1: Happy path — Create Exam returns 201 ─────────────────────────

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
                .andExpect(jsonPath("$.id").value(EXAM_ID.toString()))
                .andExpect(jsonPath("$.title").value("Haziran TYT Denemesi"))
                .andExpect(jsonPath("$.examType").value("TYT"));
    }

    // ── Test #2: Validation error — Blank exam title returns 400 ──────────────

    @Test
    @DisplayName("POST /api/v1/exams: blank title should trigger GlobalExceptionHandler and return 400")
    void createExam_blankTitle_returns400WithErrorDetails() throws Exception {
        ExamCreateRequest badRequest = new ExamCreateRequest();
        badRequest.setTitle("");
        badRequest.setExamDate(LocalDate.of(2025, 6, 15));
        badRequest.setExamType("TYT");

        mockMvc.perform(post("/api/v1/exams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.messages").isArray())
                .andExpect(jsonPath("$.messages[0]").value(
                        org.hamcrest.Matchers.containsString("title")));
    }

    // ── Test #3: Validation error — Missing date returns 400 ──────────────────

    @Test
    @DisplayName("POST /api/v1/exams: null exam date should return 400")
    void createExam_nullDate_returns400() throws Exception {
        ExamCreateRequest badRequest = new ExamCreateRequest();
        badRequest.setTitle("Valid Title");
        badRequest.setExamDate(null);
        badRequest.setExamType("TYT");

        mockMvc.perform(post("/api/v1/exams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.messages[0]").value(
                        org.hamcrest.Matchers.containsString("examDate")));
    }

    // ── Test #4: Validation error — Negative score on result submit ───────────

    @Test
    @DisplayName("POST /api/v1/exams/{id}/results: negative totalScore should return 400")
    void submitExamResult_negativeScore_returns400() throws Exception {
        SubjectResultDto subjectDto = new SubjectResultDto();
        subjectDto.setSubjectId(SUBJECT_ID);
        subjectDto.setCorrectCount(10);
        subjectDto.setWrongCount(2);
        subjectDto.setNetScore(new BigDecimal("9.50"));

        StudentExamResultRequest badRequest = new StudentExamResultRequest();
        badRequest.setStudentId(STUDENT_ID);
        badRequest.setTotalScore(new BigDecimal("-50.00"));
        badRequest.setSubjectResults(List.of(subjectDto));

        mockMvc.perform(post("/api/v1/exams/{examId}/results", EXAM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.messages[0]").value(
                        org.hamcrest.Matchers.containsString("totalScore")));
    }

    // ── Test #5: Validation error — Empty subject results list ────────────────

    @Test
    @DisplayName("POST /api/v1/exams/{id}/results: empty subjectResults should return 400")
    void submitExamResult_emptySubjectList_returns400() throws Exception {
        StudentExamResultRequest badRequest = new StudentExamResultRequest();
        badRequest.setStudentId(STUDENT_ID);
        badRequest.setTotalScore(new BigDecimal("300.00"));
        badRequest.setSubjectResults(List.of());

        mockMvc.perform(post("/api/v1/exams/{examId}/results", EXAM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }
}
