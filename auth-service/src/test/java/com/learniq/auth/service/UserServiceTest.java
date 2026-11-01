package com.learniq.auth.service;

import com.learniq.auth.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Register Role Whitelist Tests")
class UserServiceTest {

    private static final String TENANT = "akademi_1";

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(jdbcTemplate, passwordEncoder, jwtService);
    }

    private RegisterRequest request(String rawRole) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("ahmet");
        req.setPassword("Sifre!123");
        req.setEmail("ahmet@akademi.com");
        req.setFullName("Ahmet Yılmaz");
        req.setRole(rawRole);
        return req;
    }

    @Test
    @DisplayName("registerUser - whitelist rollerinden biri başarıyla kaydedilir (rol uppercase)")
    void registerUser_validRole_uppercasesRole() {
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");

        userService.registerUser(TENANT, request("teacher"));

        ArgumentCaptor<Object> roleCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(contains("INSERT INTO users"),
                any(UUID.class), anyString(), anyString(), anyString(), anyString(), roleCaptor.capture());
        assertThat(roleCaptor.getValue()).isEqualTo("TEACHER");
    }

    @Test
    @DisplayName("registerUser - whitelist dışı rol → 400, DB'ye ve şemaya hiç dokunulmaz")
    void registerUser_invalidRole_badRequest_withoutPersistence() {
        assertThatThrownBy(() -> userService.registerUser(TENANT, request("SUPER_ADMIN")))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode",
                        org.springframework.http.HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> userService.registerUser(TENANT, request("HACKER")))
                .isInstanceOf(ResponseStatusException.class);

        assertThatThrownBy(() -> userService.registerUser(TENANT, request(null)))
                .isInstanceOf(ResponseStatusException.class);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("registerUser - geçerli rolde tenant şemaya geçilir")
    void registerUser_switchesTenantSchema() {
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");

        assertThatCode(() -> userService.registerUser(TENANT, request("STUDENT")))
                .doesNotThrowAnyException();

        verify(jdbcTemplate).execute(contains("akademi_1"));
    }
}
