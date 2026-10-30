package com.learniq.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.ai.chat.AdminChatService;
import com.learniq.ai.dto.AdminChatRequest;
import com.learniq.ai.dto.AdminChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("AdminChatController Integration Tests (standalone)")
class AdminChatControllerTest {

    private AdminChatService adminChatService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        adminChatService = mock(AdminChatService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminChatController(adminChatService)).build();
    }

    private void stubChat() {
        when(adminChatService.chat(anyString(), any(AdminChatRequest.class))).thenReturn(
                new AdminChatResponse("Bağlam analizi aşağıda.",
                        List.of(new AdminChatResponse.ChatSource("CLASS_ANALYSIS_FACT", UUID.randomUUID(),
                                Map.of("classroomName", "12-A"), 0.8))));
    }

    @Test
    @DisplayName("POST /api/v1/admin/ai/chat - 200 OK (ADMIN)")
    void chat_adminRole_success() throws Exception {
        stubChat();

        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminChatRequest("Sınıf durumu?", null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Bağlam analizi aşağıda."))
                .andExpect(jsonPath("$.sources[0].sourceType").value("CLASS_ANALYSIS_FACT"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/ai/chat - 403 (rol başlığı eksik — fail-closed)")
    void chat_missingRole_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("X-Tenant-Id", "akademi_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminChatRequest("Soru", null, null, null))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminChatService);
    }

    @Test
    @DisplayName("POST /api/v1/admin/ai/chat - 403 (TEACHER rolü admin asistanına erişemez)")
    void chat_teacherRole_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminChatRequest("Soru", null, null, null))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminChatService);
    }

    @Test
    @DisplayName("POST /api/v1/admin/ai/chat - 400 (boş tenant)")
    void chat_missingTenant_badRequest() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminChatRequest("Soru", null, null, null))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminChatService);
    }

    @Test
    @DisplayName("POST /api/v1/admin/ai/chat - 400 (boş soru)")
    void chat_blankMessage_badRequest() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminChatRequest("   ", null, null, null))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminChatService);
    }

    @Test
    @DisplayName("POST /api/v1/admin/ai/chat - 400 (sınır aşımı 4000+ karakter)")
    void chat_tooLongMessage_badRequest() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminChatRequest("x".repeat(4001), null, null, null))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminChatService);
    }
}
