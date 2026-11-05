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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    @MockBean private com.learniq.exam.service.ExamFinalizationService examFinalizationService;
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
    @MockBean private com.learniq.exam.service.TeacherObservationService teacherObservationService;
    @MockBean private com.learniq.exam.service.ClassSubjectTrendCalculator classSubjectTrendCalculator;
    @MockBean private com.learniq.exam.service.SubjectTopicService subjectTopicService;
    @MockBean private com.learniq.exam.service.TeacherAnalyticsService teacherAnalyticsService;

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
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(EXAM_ID.toString()));
    }

    @Test
    @DisplayName("POST /api/v1/exams: missing role header should return 400 (fail-closed binding)")
    void createExam_missingRoleHeader_badRequest() throws Exception {
        mockMvc.perform(post("/api/v1/exams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExamCreateRequest())))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(examService);
    }

    @Test
    @DisplayName("DELETE /api/v1/exams/{id}: STUDENT role should return 403 (fail-closed)")
    void deleteExam_studentRole_forbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/exams/{examId}", EXAM_ID)
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Id", "cccccccc-0000-0000-0000-000000000001")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(examService);
    }

    @Test
    @DisplayName("POST /api/v1/exams/{id}/finalize: unassigned TEACHER should return 403")
    void finalizeExam_unassignedTeacher_forbidden() throws Exception {
        doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "You do not have access to this exam"))
                .when(accessControlService)
                .validateExamAccess(any(), any(), any(), any());

        mockMvc.perform(post("/api/v1/exams/{examId}/finalize", EXAM_ID)
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Id", "eeeeeeee-0000-0000-0000-000000000001")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isForbidden());
        verify(examFinalizationService, never()).finalizeExam(any());
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

    // ── Exam - Classroom Assignment Endpoints ────────────────────────────────

    private static final UUID CLASSROOM_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000001");

    @Test
    @DisplayName("POST /api/v1/exams/{examId}/classrooms/{classroomId} - ADMIN role returns 200 OK")
    void assignClassroom_pathVariable_admin_success() throws Exception {
        ExamResponse response = ExamResponse.builder()
                .id(EXAM_ID)
                .title("Haziran TYT Denemesi")
                .classroomIds(List.of(CLASSROOM_ID))
                .build();
        when(examService.assignClassroom(eq("akademi_1"), eq(EXAM_ID), eq(CLASSROOM_ID)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/exams/{examId}/classrooms/{classroomId}", EXAM_ID, CLASSROOM_ID)
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Id", "aaaaaaaa-0000-0000-0000-000000000001")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(EXAM_ID.toString()))
                .andExpect(jsonPath("$.classroomIds[0]").value(CLASSROOM_ID.toString()));

        verify(examService).assignClassroom("akademi_1", EXAM_ID, CLASSROOM_ID);
    }

    @Test
    @DisplayName("POST /api/v1/exams/{examId}/classrooms - Body ile atama ADMIN role returns 200 OK")
    void assignClassroom_body_admin_success() throws Exception {
        ExamResponse response = ExamResponse.builder()
                .id(EXAM_ID)
                .title("Haziran TYT Denemesi")
                .classroomIds(List.of(CLASSROOM_ID))
                .build();
        when(examService.assignClassroom(eq("akademi_1"), eq(EXAM_ID), eq(CLASSROOM_ID)))
                .thenReturn(response);

        com.learniq.exam.dto.AssignClassroomRequest req = new com.learniq.exam.dto.AssignClassroomRequest(CLASSROOM_ID);

        mockMvc.perform(post("/api/v1/exams/{examId}/classrooms", EXAM_ID)
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Id", "aaaaaaaa-0000-0000-0000-000000000001")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classroomIds[0]").value(CLASSROOM_ID.toString()));

        verify(examService).assignClassroom("akademi_1", EXAM_ID, CLASSROOM_ID);
    }

    @Test
    @DisplayName("POST /api/v1/exams/{examId}/classrooms/{classroomId} - STUDENT role returns 403 Forbidden")
    void assignClassroom_studentRole_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/exams/{examId}/classrooms/{classroomId}", EXAM_ID, CLASSROOM_ID)
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Id", STUDENT_ID.toString())
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isForbidden());

        verify(examService, never()).assignClassroom(any(), any(), any());
    }

    @Test
    @DisplayName("POST /api/v1/exams/{examId}/classrooms/{classroomId} - TEACHER yetkisiz sınıfa atama yaparsa 403 Forbidden")
    void assignClassroom_teacherUnauthorized_forbidden() throws Exception {
        UUID teacherId = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
        doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "You do not have access to this classroom"))
                .when(accessControlService)
                .validateClassroomAccess(eq("akademi_1"), eq(teacherId), eq("TEACHER"), eq(CLASSROOM_ID));

        mockMvc.perform(post("/api/v1/exams/{examId}/classrooms/{classroomId}", EXAM_ID, CLASSROOM_ID)
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Id", teacherId.toString())
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isForbidden());

        verify(examService, never()).assignClassroom(any(), any(), any());
    }

    @Test
    @DisplayName("DELETE /api/v1/exams/{examId}/classrooms/{classroomId} - ADMIN role returns 200 OK")
    void unassignClassroom_admin_success() throws Exception {
        ExamResponse response = ExamResponse.builder()
                .id(EXAM_ID)
                .title("Haziran TYT Denemesi")
                .classroomIds(List.of())
                .build();
        when(examService.unassignClassroom(eq("akademi_1"), eq(EXAM_ID), eq(CLASSROOM_ID)))
                .thenReturn(response);

        mockMvc.perform(delete("/api/v1/exams/{examId}/classrooms/{classroomId}", EXAM_ID, CLASSROOM_ID)
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Id", "aaaaaaaa-0000-0000-0000-000000000001")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());

        verify(examService).unassignClassroom("akademi_1", EXAM_ID, CLASSROOM_ID);
    }

    @Test
    @DisplayName("DELETE /api/v1/exams/{examId}/classrooms/{classroomId} - STUDENT role returns 403 Forbidden")
    void unassignClassroom_student_forbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/exams/{examId}/classrooms/{classroomId}", EXAM_ID, CLASSROOM_ID)
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Id", STUDENT_ID.toString())
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isForbidden());

        verify(examService, never()).unassignClassroom(any(), any(), any());
    }

    @Test
    @DisplayName("GET /api/v1/exams/{examId}/classrooms - ADMIN role returns 200 OK with classroom list")
    void listClassroomsForExam_admin_success() throws Exception {
        when(examService.listClassroomsForExam(eq("akademi_1"), eq(EXAM_ID)))
                .thenReturn(List.of(java.util.Map.of("id", CLASSROOM_ID.toString(), "name", "12-A")));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/exams/{examId}/classrooms", EXAM_ID)
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Id", "aaaaaaaa-0000-0000-0000-000000000001")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(CLASSROOM_ID.toString()))
                .andExpect(jsonPath("$[0].name").value("12-A"));

        verify(examService).listClassroomsForExam("akademi_1", EXAM_ID);
    }

    @Test
    @DisplayName("GET /api/v1/exams - Returns unpaged list when page and size are omitted")
    void listExams_unpaged_success() throws Exception {
        ExamResponse response = ExamResponse.builder()
                .id(EXAM_ID)
                .title("Genel Deneme")
                .build();
        when(examService.listExams()).thenReturn(List.of(response));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/exams")
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Id", "aaaaaaaa-0000-0000-0000-000000000001")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(EXAM_ID.toString()))
                .andExpect(jsonPath("$[0].title").value("Genel Deneme"));
    }

    @Test
    @DisplayName("GET /api/v1/exams?page=0&size=10 - Returns PageResponse when page and size are provided")
    void listExams_paginated_success() throws Exception {
        ExamResponse response = ExamResponse.builder()
                .id(EXAM_ID)
                .title("Genel Deneme")
                .build();
        com.learniq.common.api.PageResponse<ExamResponse> pageResponse =
                com.learniq.common.api.PageResponse.of(List.of(response), 1, 0, 10);

        when(examService.listExams(0, 10, "examDate,desc")).thenReturn(pageResponse);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/exams")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "examDate,desc")
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Id", "aaaaaaaa-0000-0000-0000-000000000001")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].id").value(EXAM_ID.toString()));
    }
}
