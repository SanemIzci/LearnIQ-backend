package com.learniq.exam.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.exam.dto.CreateObservationRequest;
import com.learniq.exam.dto.ObservationResponse;
import com.learniq.exam.dto.UpdateObservationRequest;
import com.learniq.exam.exception.EntityNotFoundException;
import com.learniq.exam.service.TeacherObservationService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = TeacherObservationController.class,
    excludeAutoConfiguration = {
        KafkaAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
    }
)
@DisplayName("TeacherObservationController Integration Tests")
class TeacherObservationControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private TeacherObservationService observationService;

    // ── Standard context mock beans ──────────────────────────────────────────
    @MockBean private com.learniq.exam.service.TeacherAnalyticsService teacherAnalyticsService;
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
    @MockBean private com.learniq.exam.service.ClassSubjectTrendCalculator classSubjectTrendCalculator;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ObservationResponse sampleResponse(UUID id, UUID teacherId, UUID classroomId) {
        return ObservationResponse.builder()
                .id(id)
                .teacherId(teacherId)
                .classroomId(classroomId)
                .content("Test gözlem notu içeriği")
                .tags(List.of("test", "gözlem"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /classrooms/{id}/observations - 201 Created")
    void create_success() throws Exception {
        UUID classroomId = UUID.randomUUID();
        UUID teacherId   = UUID.randomUUID();
        UUID obsId       = UUID.randomUUID();

        CreateObservationRequest request = new CreateObservationRequest();
        request.setContent("Sınıf bu denemede çok iyi performans gösterdi.");
        request.setTags(List.of("başarı", "TYT"));

        when(observationService.create(any(), any(), any(), any(), any()))
                .thenReturn(sampleResponse(obsId, teacherId, classroomId));

        mockMvc.perform(post("/api/v1/teachers/me/classrooms/{classroomId}/observations", classroomId)
                        .header("X-Tenant-Id", "tenant_alpha")
                        .header("X-User-Id", teacherId.toString())
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(obsId.toString()))
                .andExpect(jsonPath("$.content").value("Test gözlem notu içeriği"));
    }

    @Test
    @DisplayName("POST - STUDENT rolü 403 döndürür")
    void create_studentRole_returns403() throws Exception {
        UUID classroomId = UUID.randomUUID();
        UUID studentId   = UUID.randomUUID();

        CreateObservationRequest request = new CreateObservationRequest();
        request.setContent("Sınıf bu denemede çok iyi performans gösterdi.");

        mockMvc.perform(post("/api/v1/teachers/me/classrooms/{classroomId}/observations", classroomId)
                        .header("X-Tenant-Id", "tenant_alpha")
                        .header("X-User-Id", studentId.toString())
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(observationService);
    }

    @Test
    @DisplayName("POST - içerik boşsa 400 döndürür")
    void create_blankContent_returns400() throws Exception {
        UUID classroomId = UUID.randomUUID();
        UUID teacherId   = UUID.randomUUID();

        CreateObservationRequest request = new CreateObservationRequest();
        request.setContent("");

        mockMvc.perform(post("/api/v1/teachers/me/classrooms/{classroomId}/observations", classroomId)
                        .header("X-Tenant-Id", "tenant_alpha")
                        .header("X-User-Id", teacherId.toString())
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /classrooms/{id}/observations - 200 OK")
    void listByClassroom_success() throws Exception {
        UUID classroomId = UUID.randomUUID();
        UUID teacherId   = UUID.randomUUID();

        when(observationService.listByClassroom(any(), any(), any(), any()))
                .thenReturn(List.of(sampleResponse(UUID.randomUUID(), teacherId, classroomId)));

        mockMvc.perform(get("/api/v1/teachers/me/classrooms/{classroomId}/observations", classroomId)
                        .header("X-Tenant-Id", "tenant_alpha")
                        .header("X-User-Id", teacherId.toString())
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].content").value("Test gözlem notu içeriği"));
    }

    @Test
    @DisplayName("GET /observations/{id} - 200 OK")
    void getById_success() throws Exception {
        UUID teacherId = UUID.randomUUID();
        UUID obsId     = UUID.randomUUID();

        when(observationService.getById(any(), eq(obsId)))
                .thenReturn(sampleResponse(obsId, teacherId, UUID.randomUUID()));

        mockMvc.perform(get("/api/v1/teachers/me/observations/{id}", obsId)
                        .header("X-User-Id", teacherId.toString())
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(obsId.toString()));
    }

    @Test
    @DisplayName("GET /observations/{id} - bulunamayan not 404 döndürür")
    void getById_notFound_returns404() throws Exception {
        UUID teacherId = UUID.randomUUID();
        UUID obsId     = UUID.randomUUID();

        when(observationService.getById(any(), eq(obsId)))
                .thenThrow(new EntityNotFoundException("TeacherObservation", obsId));

        mockMvc.perform(get("/api/v1/teachers/me/observations/{id}", obsId)
                        .header("X-User-Id", teacherId.toString())
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /observations/{id} - 200 OK")
    void update_success() throws Exception {
        UUID teacherId = UUID.randomUUID();
        UUID obsId     = UUID.randomUUID();

        UpdateObservationRequest request = new UpdateObservationRequest();
        request.setContent("Güncellenmiş içerik minimum uzunlukta");

        when(observationService.update(any(), eq(obsId), any()))
                .thenReturn(sampleResponse(obsId, teacherId, UUID.randomUUID()));

        mockMvc.perform(put("/api/v1/teachers/me/observations/{id}", obsId)
                        .header("X-User-Id", teacherId.toString())
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(obsId.toString()));
    }

    @Test
    @DisplayName("DELETE /observations/{id} - 204 No Content")
    void delete_success() throws Exception {
        UUID teacherId = UUID.randomUUID();
        UUID obsId     = UUID.randomUUID();

        doNothing().when(observationService).delete(any(), eq(obsId));

        mockMvc.perform(delete("/api/v1/teachers/me/observations/{id}", obsId)
                        .header("X-User-Id", teacherId.toString())
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isNoContent());
    }
}
