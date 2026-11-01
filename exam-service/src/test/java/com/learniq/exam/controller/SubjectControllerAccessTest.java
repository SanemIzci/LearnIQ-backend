package com.learniq.exam.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.exam.dto.SubjectRequest;
import com.learniq.exam.dto.SubjectResponse;
import com.learniq.exam.service.SubjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("SubjectController Access-Control Integration Tests (standalone)")
class SubjectControllerAccessTest {

    private SubjectService subjectService;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        subjectService = mock(SubjectService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SubjectController(subjectService)).build();
    }

    private SubjectRequest validRequest() {
        SubjectRequest request = new SubjectRequest();
        request.setName("Fizik");
        return request;
    }

    @Test
    @DisplayName("POST /api/v1/subjects - rol header'ı yoksa 403 (fail-closed), servis çağrılmaz")
    void createSubject_missingRole_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(subjectService);
    }

    @Test
    @DisplayName("POST /api/v1/subjects - STUDENT 403 alır, servis çağrılmaz")
    void createSubject_student_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/subjects")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(subjectService);
    }

    @Test
    @DisplayName("POST /api/v1/subjects - TEACHER ders oluşturabilir (201)")
    void createSubject_teacher_ok() throws Exception {
        UUID subjectId = UUID.randomUUID();
        when(subjectService.createSubject(any())).thenReturn(
                SubjectResponse.builder().id(subjectId).name("Fizik").build());

        mockMvc.perform(post("/api/v1/subjects")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Fizik"));
    }
}
