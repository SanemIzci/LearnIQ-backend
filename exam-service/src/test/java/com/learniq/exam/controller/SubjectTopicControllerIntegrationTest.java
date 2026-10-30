package com.learniq.exam.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.exam.dto.CreateSubjectTopicRequest;
import com.learniq.exam.dto.SubjectTopicResponse;
import com.learniq.exam.dto.UpdateSubjectTopicRequest;
import com.learniq.exam.service.SubjectTopicService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = SubjectTopicController.class,
    excludeAutoConfiguration = {
        KafkaAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
    }
)
@DisplayName("SubjectTopicController Integration Tests")
class SubjectTopicControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean private SubjectTopicService subjectTopicService;
    @MockBean private com.learniq.exam.service.TeacherAnalyticsService teacherAnalyticsService;

    // Standard context mock beans to prevent application context bootstrap errors
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
    @DisplayName("GET /api/v1/subjects/{subjectId}/topics - 200 OK")
    void listTopics_success() throws Exception {
        UUID subjectId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();

        SubjectTopicResponse topic = SubjectTopicResponse.builder()
                .id(topicId)
                .subjectId(subjectId)
                .name("Kuvvet ve Hareket")
                .code("FIZ-01")
                .orderIndex(1)
                .createdAt(LocalDateTime.now())
                .build();

        when(subjectTopicService.listTopicsBySubject(subjectId)).thenReturn(List.of(topic));

        mockMvc.perform(get("/api/v1/subjects/{subjectId}/topics", subjectId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(topicId.toString()))
                .andExpect(jsonPath("$[0].name").value("Kuvvet ve Hareket"))
                .andExpect(jsonPath("$[0].code").value("FIZ-01"))
                .andExpect(jsonPath("$[0].orderIndex").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/subjects/{subjectId}/topics/{topicId} - 200 OK")
    void getTopic_success() throws Exception {
        UUID subjectId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();

        SubjectTopicResponse topic = SubjectTopicResponse.builder()
                .id(topicId)
                .subjectId(subjectId)
                .name("Kuvvet ve Hareket")
                .code("FIZ-01")
                .orderIndex(1)
                .build();

        when(subjectTopicService.getTopic(subjectId, topicId)).thenReturn(topic);

        mockMvc.perform(get("/api/v1/subjects/{subjectId}/topics/{topicId}", subjectId, topicId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(topicId.toString()))
                .andExpect(jsonPath("$.name").value("Kuvvet ve Hareket"));
    }

    @Test
    @DisplayName("POST /api/v1/subjects/{subjectId}/topics - 201 Created (TEACHER)")
    void createTopic_teacherRole_success() throws Exception {
        UUID subjectId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();

        CreateSubjectTopicRequest request = CreateSubjectTopicRequest.builder()
                .name("Optik")
                .code("FIZ-OPT")
                .orderIndex(2)
                .build();

        SubjectTopicResponse response = SubjectTopicResponse.builder()
                .id(topicId)
                .subjectId(subjectId)
                .name("Optik")
                .code("FIZ-OPT")
                .orderIndex(2)
                .build();

        when(subjectTopicService.createTopic(eq(subjectId), any(CreateSubjectTopicRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/subjects/{subjectId}/topics", subjectId)
                .header("X-User-Role", "TEACHER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(topicId.toString()))
                .andExpect(jsonPath("$.name").value("Optik"));
    }

    @Test
    @DisplayName("POST - STUDENT rolü 403 döndürür")
    void createTopic_studentRole_forbidden() throws Exception {
        UUID subjectId = UUID.randomUUID();

        CreateSubjectTopicRequest request = CreateSubjectTopicRequest.builder()
                .name("Optik")
                .build();

        mockMvc.perform(post("/api/v1/subjects/{subjectId}/topics", subjectId)
                .header("X-User-Role", "STUDENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST - rol header'ı yoksa fail-closed 403 döndürür")
    void createTopic_missingRole_forbidden() throws Exception {
        UUID subjectId = UUID.randomUUID();

        CreateSubjectTopicRequest request = CreateSubjectTopicRequest.builder()
                .name("Optik")
                .build();

        mockMvc.perform(post("/api/v1/subjects/{subjectId}/topics", subjectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(subjectTopicService, never()).createTopic(any(), any());
    }

    @Test
    @DisplayName("POST /api/v1/subjects/{subjectId}/topics - 400 Bad Request on blank name")
    void createTopic_blankName_badRequest() throws Exception {
        UUID subjectId = UUID.randomUUID();

        CreateSubjectTopicRequest request = CreateSubjectTopicRequest.builder()
                .name("")
                .build();

        mockMvc.perform(post("/api/v1/subjects/{subjectId}/topics", subjectId)
                .header("X-User-Role", "TEACHER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/v1/subjects/{subjectId}/topics/{topicId} - 200 OK (ADMIN)")
    void updateTopic_adminRole_success() throws Exception {
        UUID subjectId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();

        UpdateSubjectTopicRequest request = UpdateSubjectTopicRequest.builder()
                .name("Dalgalar ve Optik")
                .code("FIZ-DALGA")
                .orderIndex(3)
                .build();

        SubjectTopicResponse response = SubjectTopicResponse.builder()
                .id(topicId)
                .subjectId(subjectId)
                .name("Dalgalar ve Optik")
                .code("FIZ-DALGA")
                .orderIndex(3)
                .build();

        when(subjectTopicService.updateTopic(eq(subjectId), eq(topicId), any(UpdateSubjectTopicRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/subjects/{subjectId}/topics/{topicId}", subjectId, topicId)
                .header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Dalgalar ve Optik"));
    }

    @Test
    @DisplayName("DELETE /api/v1/subjects/{subjectId}/topics/{topicId} - 204 No Content")
    void deleteTopic_success() throws Exception {
        UUID subjectId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();

        doNothing().when(subjectTopicService).deleteTopic(subjectId, topicId);

        mockMvc.perform(delete("/api/v1/subjects/{subjectId}/topics/{topicId}", subjectId, topicId)
                .header("X-User-Role", "TEACHER"))
                .andExpect(status().isNoContent());
    }
}
