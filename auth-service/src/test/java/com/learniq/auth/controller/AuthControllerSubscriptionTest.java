package com.learniq.auth.controller;

import com.learniq.auth.dto.LoginRequest;
import com.learniq.auth.exception.AuthExceptionHandler;
import com.learniq.auth.service.JwtService;
import com.learniq.auth.service.TenantSubscriptionService;
import com.learniq.auth.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AuthController Subscription Enforcement Tests (Faz B)")
class AuthControllerSubscriptionTest {

    private UserService userService;
    private TenantSubscriptionService tenantSubscriptionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        tenantSubscriptionService = mock(TenantSubscriptionService.class);
        JwtService jwtService = mock(JwtService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AuthController(jwtService, userService, tenantSubscriptionService))
                .setControllerAdvice(new AuthExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("login - SUSPENDED tenant → 403, kullanıcılar aramaya geçilmez")
    void login_suspendedTenant_forbidden() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant is SUSPENDED"))
                .when(tenantSubscriptionService).requireActiveTenant("akademi_1");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json("deniz", "Pw123456", "akademi_1")))
                .andExpect(status().isForbidden());

        verify(userService, never()).findUserByUsername(anyString(), anyString());
    }

    @Test
    @DisplayName("login - ACTIVE tenant → kullanıcı akışı çalışır")
    void login_activeTenant_proceeds() throws Exception {
        doNothing().when(tenantSubscriptionService).requireActiveTenant("akademi_1");
        when(userService.findUserByUsername("akademi_1", "deniz"))
                .thenReturn(Map.of("active", true, "password_hash", "$2a$10$", "role", "STUDENT", "id", UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json("deniz", "Pw123456", "akademi_1")))
                .andExpect(status().isUnauthorized());

        verify(tenantSubscriptionService).requireActiveTenant("akademi_1");
        verify(userService).findUserByUsername("akademi_1", "deniz");
    }

    @Test
    @DisplayName("register - suspend edilmiş tenant'a kullanıcı eklenemez")
    void register_suspendedTenant_forbidden() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant is SUSPENDED"))
                .when(tenantSubscriptionService).requireActiveTenant("akademi_1");

        mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-User-Role", "ADMIN")
                        .header("X-Tenant-Id", "akademi_1")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"yeni","password":"Pw123456","email":"y@x.io",
                                 "fullName":"Yeni","role":"STUDENT","tenantId":"akademi_1"}
                                """))
                .andExpect(status().isForbidden());

        verify(userService, never()).registerUser(anyString(), any());
    }

    @Test
    @DisplayName("register - body tenantId differs from X-Tenant-Id header → 400 Bad Request (cross-tenant rejected)")
    void register_crossTenantMismatch_badRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-User-Role", "ADMIN")
                        .header("X-Tenant-Id", "akademi_1")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"attacker","password":"Pw123456","email":"a@x.io",
                                 "fullName":"Attacker","role":"STUDENT","tenantId":"akademi_victim"}
                                """))
                .andExpect(status().isBadRequest());

        verify(userService, never()).registerUser(anyString(), any());
        verify(tenantSubscriptionService, never()).requireActiveTenant(anyString());
    }

    @Test
    @DisplayName("register - missing X-Tenant-Id header → 400 Bad Request")
    void register_missingTenantHeader_badRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-User-Role", "ADMIN")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"yeni","password":"Pw123456","email":"y@x.io",
                                 "fullName":"Yeni","role":"STUDENT","tenantId":"akademi_1"}
                                """))
                .andExpect(status().isBadRequest());

        verify(userService, never()).registerUser(anyString(), any());
    }

    @Test
    @DisplayName("register - non-ADMIN role → 403 Forbidden")
    void register_nonAdminRole_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-User-Role", "TEACHER")
                        .header("X-Tenant-Id", "akademi_1")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"yeni","password":"Pw123456","email":"y@x.io",
                                 "fullName":"Yeni","role":"STUDENT","tenantId":"akademi_1"}
                                """))
                .andExpect(status().isForbidden());

        verify(userService, never()).registerUser(anyString(), any());
    }

    @Test
    @DisplayName("register - matching tenantId and active tenant → 201 Created under authenticated tenant")
    void register_validAdmin_createsUser() throws Exception {
        doNothing().when(tenantSubscriptionService).requireActiveTenant("akademi_1");
        UUID createdId = UUID.randomUUID();
        when(userService.registerUser(eq("akademi_1"), any())).thenReturn(createdId);

        mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-User-Role", "ADMIN")
                        .header("X-Tenant-Id", "akademi_1")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"valid","password":"Pw123456","email":"v@x.io",
                                 "fullName":"Valid","role":"STUDENT","tenantId":"akademi_1"}
                                """))
                .andExpect(status().isCreated());

        verify(tenantSubscriptionService).requireActiveTenant("akademi_1");
        verify(userService).registerUser(eq("akademi_1"), any());
    }

    private static String json(String username, String password, String tenantId) {
        return "{\"username\":\"%s\",\"password\":\"%s\",\"tenantId\":\"%s\"}".formatted(username, password, tenantId);
    }
}
