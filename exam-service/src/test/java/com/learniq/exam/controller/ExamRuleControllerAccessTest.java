package com.learniq.exam.controller;

import com.learniq.exam.dto.ExamTypeSubjectRuleRequest;
import com.learniq.exam.service.ExamRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ExamRuleController Access Tests (standalone)")
class ExamRuleControllerAccessTest {

    private ExamRuleService examRuleService;
    private MockMvc mockMvc;

    private static final UUID SUBJECT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        examRuleService = mock(ExamRuleService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ExamRuleController(examRuleService)).build();
    }

    @Test
    @DisplayName("PUT - TEACHER uygulantır; request body doğrulanır")
    void upsertRule_teacher_ok() throws Exception {
        mockMvc.perform(put("/api/v1/exam-rules/{examType}/{subjectId}", "TYT", SUBJECT_ID)
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionCount\":40,\"penaltyDivisor\":4.0}"))
                .andExpect(status().isOk());

        verify(examRuleService).upsertRule(any(), eq(SUBJECT_ID), any(ExamTypeSubjectRuleRequest.class));
    }

    @Test
    @DisplayName("PUT - STUDENT 403 alır; servis çağrılmaz")
    void upsertRule_student_forbidden() throws Exception {
        mockMvc.perform(put("/api/v1/exam-rules/{examType}/{subjectId}", "TYT", SUBJECT_ID)
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionCount\":40}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(examRuleService);
    }

    @Test
    @DisplayName("PUT - questionCount 0 ise query validation sonucu 400 döer")
    void upsertRule_negativeQuestionCount_badRequest() throws Exception {
        mockMvc.perform(put("/api/v1/exam-rules/{examType}/{subjectId}", "TYT", SUBJECT_ID)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionCount\":0}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(examRuleService);
    }

    @Test
    @DisplayName("GET - öğretmen listeleme yapabilir")
    void listRules_teacher_ok() throws Exception {
        when(examRuleService.listRules("TYT")).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/exam-rules/{examType}", "TYT")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk());

        verify(examRuleService).listRules("TYT");
    }

    @Test
    @DisplayName("GET - X-User-Role header yoksa 403 (fail-closed)")
    void listRules_missingRole_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/exam-rules/{examType}", "TYT"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(examRuleService);
    }

    @Test
    @DisplayName("PUT - X-User-Role header'ı eksikse 403")
    void upsertRule_missingRole_forbidden() throws Exception {
        mockMvc.perform(put("/api/v1/exam-rules/{examType}/{subjectId}", "TYT", SUBJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionCount\":40}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(examRuleService);
    }
}
