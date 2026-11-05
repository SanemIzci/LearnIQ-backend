package com.learniq.auth.config;

import com.learniq.auth.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("SecurityConfig Security Access Rules")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SecurityAccessTest.TestConfig.class)
@WebAppConfiguration
@org.springframework.test.context.TestPropertySource(properties = {
        "jwt.secret=mustBeAtLeast32CharsLongForHS256Security!",
        "jwt.access-token-expiry-ms=3600000"
})
class SecurityAccessTest {

    private static final String SECRET = "mustBeAtLeast32CharsLongForHS256Security!";
    private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Autowired
    private JwtService jwtService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp(WebApplicationContext wac) {
        FilterChainProxy springSecurityFilterChain = wac.getBean("springSecurityFilterChain", FilterChainProxy.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/platform/auth/login is permitAll — no Bearer token required")
    void platformLogin_isPermitAll() throws Exception {
        mockMvc.perform(post("/api/v1/platform/auth/login"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login is permitAll — no Bearer token required")
    void tenantLogin_isPermitAll() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/users without Bearer token is 401 Authentication Required")
    void usersEndpoint_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/users with valid Bearer token passes SecurityFilterChain")
    void usersEndpoint_withValidToken_isAuthorized() throws Exception {
        String token = jwtService.generateAccessToken(USER_ID, "adminUser", "akademi_1", "ADMIN");

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Configuration
    @EnableWebSecurity
    @org.springframework.web.servlet.config.annotation.EnableWebMvc
    static class TestConfig {

        @Bean
        public JwtService jwtService() {
            JwtService service = new JwtService();
            ReflectionTestUtils.setField(service, "secret", SECRET);
            ReflectionTestUtils.setField(service, "accessTokenExpiryMs", 3600_000L);
            return service;
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
            return new SecurityConfig(jwtService).securityFilterChain(http);
        }

        @Bean
        public DummyController dummyController() {
            return new DummyController();
        }
    }

    @RestController
    static class DummyController {
        @PostMapping("/api/v1/platform/auth/login")
        public String platformLogin() { return "ok"; }

        @PostMapping("/api/v1/auth/login")
        public String authLogin() { return "ok"; }

        @GetMapping("/api/v1/users")
        public String users() { return "ok"; }
    }
}
