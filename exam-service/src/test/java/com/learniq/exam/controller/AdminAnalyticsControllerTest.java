package com.learniq.exam.controller;

import com.learniq.exam.dto.AdminDashboardResponse;
import com.learniq.exam.dto.ClassroomAdminSummary;
import com.learniq.exam.dto.SubjectScorecardResponse;
import com.learniq.exam.dto.SubjectScorecardRow;
import com.learniq.exam.exception.GlobalExceptionHandler;
import com.learniq.exam.service.AdminAnalyticsService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AdminAnalyticsController Fail-Closed Tests (standalone)")
class AdminAnalyticsControllerTest {

    private static final UUID EXAM_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final UUID SUBJECT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000001");

    private AdminAnalyticsService adminAnalyticsService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        adminAnalyticsService = mock(AdminAnalyticsService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AdminAnalyticsController(adminAnalyticsService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private void stubDashboard() {
        when(adminAnalyticsService.getDashboard(anyString()))
                .thenReturn(AdminDashboardResponse.builder()
                        .classroomCount(1)
                        .classrooms(List.of(ClassroomAdminSummary.builder()
                                .classroomName("9A")
                                .avgNet(new java.math.BigDecimal("15.25"))
                                .upTrendCount(2)
                                .downTrendCount(1)
                                .stableTrendCount(3)
                                .build()))
                        .build());
    }

    @Test
    @DisplayName("dashboard - rol header'ı eksik → 400 (fail-closed)")
    void dashboard_missingRole_badRequest() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/admin/dashboard")
                        .header("X-Tenant-Id", "akademi_1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminAnalyticsService);
    }

    @Test
    @DisplayName("dashboard - TEACHER → 403; tenant header'ı eksik → 400")
    void dashboard_wrongOrMissingAuth() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/admin/dashboard")
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/analytics/admin/dashboard")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminAnalyticsService);
    }

    @Test
    @DisplayName("dashboard - ADMIN → 200 + özet satırları")
    void dashboard_admin_ok() throws Exception {
        stubDashboard();

        mockMvc.perform(get("/api/v1/analytics/admin/dashboard")
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classroomCount").value(1))
                .andExpect(jsonPath("$.classrooms[0].classroomName").value("9A"));

        verify(adminAnalyticsService).getDashboard("akademi_1");
    }

    @Test
    @DisplayName("dashboard - SUPER_ADMIN de erişebilir")
    void dashboard_superAdmin_ok() throws Exception {
        stubDashboard();

        mockMvc.perform(get("/api/v1/analytics/admin/dashboard")
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Role", "SUPER_ADMIN"))
                .andExpect(status().isOk());

        verify(adminAnalyticsService).getDashboard("akademi_1");
    }

    @Test
    @DisplayName("subjects - STUDENT → 403, ADMIN + examId parametresi → 200")
    void subjects_guardAndParam() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/admin/subjects")
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isForbidden());

        when(adminAnalyticsService.getSubjectScorecard(anyString(), eq(EXAM_ID)))
                .thenReturn(SubjectScorecardResponse.builder()
                        .examId(EXAM_ID)
                        .subjects(List.of(SubjectScorecardRow.builder()
                                .subjectName("Matematik")
                                .avgNet(new java.math.BigDecimal("15.50"))
                                .build()))
                        .build());

        mockMvc.perform(get("/api/v1/analytics/admin/subjects")
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Role", "ADMIN")
                        .param("examId", EXAM_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.examId").value(EXAM_ID.toString()))
                .andExpect(jsonPath("$.subjects[0].subjectName").value("Matematik"));
    }

    @Test
    @DisplayName("trends - limit clamp: 999→200, 0→50; subjectId parametresi iletilir")
    void trends_limitClamp() throws Exception {
        when(adminAnalyticsService.getTrends(anyString(), isNull(), anyInt())).thenReturn(List.of());
        when(adminAnalyticsService.getTrends(anyString(), eq(SUBJECT_ID), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/analytics/admin/trends")
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Role", "ADMIN")
                        .param("limit", "999"))
                .andExpect(status().isOk());
        verify(adminAnalyticsService).getTrends("akademi_1", null, 200);

        mockMvc.perform(get("/api/v1/analytics/admin/trends")
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Role", "ADMIN")
                        .param("limit", "0")
                        .param("subjectId", SUBJECT_ID.toString()))
                .andExpect(status().isOk());
        verify(adminAnalyticsService).getTrends("akademi_1", SUBJECT_ID, 50);
    }

    @Test
    @DisplayName("observations/recent - clamp ve varsayılan 10")
    void observations_clampAndDefault() throws Exception {
        when(adminAnalyticsService.getRecentObservations(anyString(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/analytics/admin/observations/recent")
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
        verify(adminAnalyticsService).getRecentObservations("akademi_1", 10);

        mockMvc.perform(get("/api/v1/analytics/admin/observations/recent")
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Role", "ADMIN")
                        .param("limit", "1000"))
                .andExpect(status().isOk());
        verify(adminAnalyticsService).getRecentObservations("akademi_1", 50);
    }
}
