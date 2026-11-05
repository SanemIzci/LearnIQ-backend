package com.learniq.auth.controller;

import com.learniq.auth.exception.AuthExceptionHandler;
import com.learniq.auth.service.JwtService;
import com.learniq.auth.service.UserService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("PlatformAuthController Unit & Contract Tests")
class PlatformAuthControllerTest {

    private static final String SECRET = "mustBeAtLeast32CharsLongForHS256Security!";
    private static final UUID SUPER_ADMIN_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private JwtService jwtService;
    private UserService userService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiryMs", 900_000L); // 15 min

        userService = mock(UserService.class);

        PlatformAuthController controller = new PlatformAuthController(jwtService, userService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AuthExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("login - valid credentials returns 200, SUPER_ADMIN JWT without tenantId, null refresh token")
    void login_validCredentials_returnsSuperAdminToken() throws Exception {
        when(userService.findGlobalUser("superadmin")).thenReturn(Map.of(
                "id", SUPER_ADMIN_ID.toString(),
                "username", "superadmin",
                "password_hash", "$2a$10$hashedPassword",
                "role", "SUPER_ADMIN"
        ));
        when(userService.verifyPassword("AdminPassword123!", "$2a$10$hashedPassword")).thenReturn(true);

        MvcResult result = mockMvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"superadmin","password":"AdminPassword123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.username").value("superadmin"))
                .andExpect(jsonPath("$.userId").value(SUPER_ADMIN_ID.toString()))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        // Extract and cryptographically verify the JWT claims
        String responseContent = result.getResponse().getContentAsString();
        String accessToken = com.jayway.jsonpath.JsonPath.read(responseContent, "$.accessToken");
        assertThat(accessToken).isNotBlank();

        Claims claims = jwtService.parseToken(accessToken);
        assertThat(claims.getSubject()).isEqualTo("superadmin");
        assertThat(claims.get("role", String.class)).isEqualTo("SUPER_ADMIN");
        assertThat(claims.get("userId", String.class)).isEqualTo(SUPER_ADMIN_ID.toString());
        // CRITICAL: Platform token must carry NO tenantId claim
        assertThat(claims.get("tenantId", String.class)).isNull();
    }

    @Test
    @DisplayName("login - unknown username returns 401 Unauthorized")
    void login_unknownUser_returns401() throws Exception {
        when(userService.findGlobalUser("nonexistent")).thenReturn(null);

        mockMvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"nonexistent","password":"somePassword"}
                                """))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).verifyPassword(anyString(), anyString());
    }

    @Test
    @DisplayName("login - wrong password returns 401 Unauthorized")
    void login_wrongPassword_returns401() throws Exception {
        when(userService.findGlobalUser("superadmin")).thenReturn(Map.of(
                "id", SUPER_ADMIN_ID.toString(),
                "username", "superadmin",
                "password_hash", "$2a$10$correctHash",
                "role", "SUPER_ADMIN"
        ));
        when(userService.verifyPassword("wrongPassword", "$2a$10$correctHash")).thenReturn(false);

        mockMvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"superadmin","password":"wrongPassword"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login - missing username or password returns 400 Bad Request")
    void login_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"superadmin"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/platform/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"password":"somePassword"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }
}
