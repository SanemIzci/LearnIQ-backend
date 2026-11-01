package com.learniq.exam.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.exam.dto.RejectionRequest;
import com.learniq.exam.repository.StudentExamResultRepository;
import com.learniq.exam.service.ExamResultService;
import com.learniq.exam.service.ExamService;
import com.learniq.exam.security.AccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ExamResultController Access-Control Integration Tests (standalone)")
class ExamResultControllerAccessTest {

    private static final UUID EXAM_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID RESULT_ID = UUID.randomUUID();
    private static final UUID TEACHER_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");

    private ExamResultService examResultService;
    private AccessControlService accessControlService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        examResultService = mock(ExamResultService.class);
        accessControlService = mock(AccessControlService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ExamResultController(
                examResultService, mock(StudentExamResultRepository.class), accessControlService)).build();
    }

    @Test
    @DisplayName("POST approve - atanmış TEACHER onaylama akışını çalıştırır")
    void approveResult_assignedTeacher_ok() throws Exception {
        mockMvc.perform(post("/api/v1/exams/{examId}/results/{resultId}/approve", EXAM_ID, RESULT_ID)
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Id", TEACHER_ID.toString())
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk());

        verify(examResultService).approveResult(eq(EXAM_ID), eq(RESULT_ID), eq(TEACHER_ID));
    }

    @Test
    @DisplayName("POST approve - atanmamış TEACHER 403 alır, servis çağrılmaz")
    void approveResult_unassignedTeacher_forbidden() throws Exception {
        doThrow(new ResponseStatusException(FORBIDDEN, "You do not have access to this exam"))
                .when(accessControlService)
                .validateExamAccess(any(), any(), any(), any());

        mockMvc.perform(post("/api/v1/exams/{examId}/results/{resultId}/approve", EXAM_ID, RESULT_ID)
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Id", TEACHER_ID.toString())
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(examResultService);
    }

    @Test
    @DisplayName("POST reject - STUDENT 403 alır (rol guard)")
    void rejectResult_studentRole_forbidden() throws Exception {
        RejectionRequest body = new RejectionRequest();
        body.setReason("Hatalı giriş");

        mockMvc.perform(post("/api/v1/exams/{examId}/results/{resultId}/reject", EXAM_ID, RESULT_ID)
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Id", "cccccccc-0000-0000-0000-000000000001")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(examResultService);
    }

    @Test
    @DisplayName("POST reject - X-Tenant-Id header'ı eksikse 400 (ziyaretçi şema geçişi yapılamaz)")
    void rejectResult_missingTenant_badRequest() throws Exception {
        RejectionRequest body = new RejectionRequest();
        body.setReason("Hatalı giriş");

        mockMvc.perform(post("/api/v1/exams/{examId}/results/{resultId}/reject", EXAM_ID, RESULT_ID)
                        .header("X-User-Id", TEACHER_ID.toString())
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        verify(examResultService, never()).rejectResult(any(), any(), any(), any());
    }
}
