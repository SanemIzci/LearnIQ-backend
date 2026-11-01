package com.learniq.auth.controller;

import com.learniq.auth.dto.AdminUserSummary;
import com.learniq.auth.dto.PatchUserRequest;
import com.learniq.auth.dto.UserListResponse;
import com.learniq.auth.exception.AuthExceptionHandler;
import com.learniq.auth.service.AdminUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AdminUserController Fail-Closed Tests (standalone)")
class AdminUserControllerTest {

    private static final UUID TARGET = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    private static final UUID REQUESTER = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");

    private AdminUserService adminUserService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        adminUserService = mock(AdminUserService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminUserController(adminUserService))
                .setControllerAdvice(new AuthExceptionHandler())
                .build();
    }

    private void stubList() {
        when(adminUserService.listUsers(anyString(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new UserListResponse(1, 0, 20, List.of(new AdminUserSummary(
                        TARGET, "ayse", "ayse@x.com", "Ayşe", "STUDENT", true, null))));
    }

    @Test
    @DisplayName("listUsers - rol header'ı eksik → 400; TEACHER → 403; ADMIN → 200")
    void listUsers_guardMatrix() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("X-Tenant-Id", "akademi_1"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/users")
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isForbidden());

        stubList();
        mockMvc.perform(get("/api/v1/users")
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("ayse"));
    }

    @Test
    @DisplayName("listUsers - sayfa sınırları: page<0→0, size>100→100")
    void listUsers_paginationClamp() throws Exception {
        stubList();

        mockMvc.perform(get("/api/v1/users")
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Role", "ADMIN")
                        .param("page", "-5")
                        .param("size", "500"))
                .andExpect(status().isOk());

        verify(adminUserService).listUsers("akademi_1", null, null, 0, 100);
    }

    @Test
    @DisplayName("getUserDetail - SUPER_ADMIN erişebilir")
    void getUserDetail_superAdmin_ok() throws Exception {
        when(adminUserService.getUserDetail(anyString(), eq(TARGET)))
                .thenReturn(new AdminUserSummary(TARGET, "ayse", "ayse@x.com", "Ayşe", "STUDENT", true, null));

        mockMvc.perform(get("/api/v1/users/" + TARGET)
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Role", "SUPER_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    @DisplayName("PATCH - STUDENT → 403; ADMIN → delegasyon + X-User-Id iletilir")
    void updateUser_guardAndDelegation() throws Exception {
        mockMvc.perform(patch("/api/v1/users/" + TARGET)
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Id", REQUESTER.toString())
                        .header("X-User-Role", "STUDENT")
                        .contentType(APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());

        when(adminUserService.updateUser(eq("akademi_1"), eq(REQUESTER), eq(TARGET), any()))
                .thenReturn(new AdminUserSummary(TARGET, "ayse", "ayse@x.com", "Yeni", "STUDENT", false, null));

        mockMvc.perform(patch("/api/v1/users/" + TARGET)
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Id", REQUESTER.toString())
                        .header("X-User-Role", "ADMIN")
                        .contentType(APPLICATION_JSON)
                        .content("{\"active\":false,\"fullName\":\"Yeni\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        verify(adminUserService).updateUser(eq("akademi_1"), eq(REQUESTER), eq(TARGET), any(PatchUserRequest.class));
    }

    @Test
    @DisplayName("reset-password - ADMIN → 200 reset=true; servis 400 fırlatırsa handler'a düşer")
    void resetPassword_adminOk() throws Exception {
        doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Password must be at least 8 characters"))
                .when(adminUserService).resetPassword(eq("akademi_1"), eq(TARGET), isNull());

        mockMvc.perform(post("/api/v1/users/" + TARGET + "/reset-password")
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Role", "ADMIN")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/users/" + TARGET + "/reset-password")
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Role", "ADMIN")
                        .contentType(APPLICATION_JSON)
                        .content("{\"newPassword\":\"YeniSifre123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reset").value(true));

        verify(adminUserService).resetPassword("akademi_1", TARGET, "YeniSifre123");
    }
}
