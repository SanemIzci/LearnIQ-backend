package com.learniq.exam.controller;

import com.learniq.common.dto.StudentTrendReportDto;
import com.learniq.exam.security.AccessControlService;
import com.learniq.exam.service.AnalyticsEventPublisher;
import com.learniq.exam.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AnalyticsController Fail-Closed Integration Tests (standalone)")
class AnalyticsControllerAccessTest {

    private static final UUID STUDENT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    private static final UUID TEACHER_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");

    private AnalyticsService analyticsService;
    private AnalyticsEventPublisher analyticsEventPublisher;
    private AccessControlService accessControlService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        analyticsService = mock(AnalyticsService.class);
        analyticsEventPublisher = mock(AnalyticsEventPublisher.class);
        accessControlService = mock(AccessControlService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AnalyticsController(analyticsService, analyticsEventPublisher, accessControlService)).build();
    }

    private void stubTrendReport() {
        when(analyticsService.getStudentTrendReport(any(UUID.class)))
                .thenReturn(StudentTrendReportDto.builder()
                        .studentId(STUDENT_ID)
                        .totalExamsTaken(1)
                        .examHistory(List.of())
                        .build());
    }

    @Test
    @DisplayName("GET history - rol header'ı eksikse 403 (fail-closed, default STUDENT yok)")
    void getStudentHistory_missingRole_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/students/{id}/history", STUDENT_ID)
                        .header("X-Tenant-Id", "akademi_1"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(analyticsService);
    }

    @Test
    @DisplayName("GET history - tenant header'ı eksikse 403 (default 'public' yok)")
    void getStudentHistory_missingTenant_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/students/{id}/history", STUDENT_ID)
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(analyticsService);
    }

    @Test
    @DisplayName("GET history - STUDENT userId'siz → requesterId=self fallback ile 200")
    void getStudentHistory_studentWithoutUserId_selfFallback() throws Exception {
        stubTrendReport();

        mockMvc.perform(get("/api/v1/analytics/students/{id}/history", STUDENT_ID)
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk());

        verify(accessControlService).validateStudentAccess(
                eq("akademi_1"), eq(STUDENT_ID), eq("STUDENT"), eq(STUDENT_ID));
    }

    @Test
    @DisplayName("GET history - TEACHER userId'siz → requesterId fallback YOK, 403")
    void getStudentHistory_teacherWithoutUserId_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/students/{id}/history", STUDENT_ID)
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(analyticsService);
    }

    @Test
    @DisplayName("POST generate - TEACHER erişimi çervice izniyle kabul edilir (202)")
    void generateAiReport_teacher_ok() throws Exception {
        stubTrendReport();

        mockMvc.perform(post("/api/v1/analytics/students/{id}/reports/generate", STUDENT_ID)
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Id", TEACHER_ID.toString())
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isAccepted());

        verify(analyticsEventPublisher).publishAiReportRequest(eq(STUDENT_ID), any());
    }
}
