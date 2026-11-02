package com.learniq.auth.service;

import com.learniq.auth.dto.CreateGlobalUserRequest;
import com.learniq.auth.dto.GlobalUserSummary;
import com.learniq.auth.dto.GlobalUserListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalUserService Unit Tests (Faz B)")
class GlobalUserServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PasswordEncoder passwordEncoder;

    private GlobalUserService service;

    @BeforeEach
    void setUp() {
        service = new GlobalUserService(jdbcTemplate, passwordEncoder);
    }

    private static final UUID TARGET = UUID.fromString("dddddddd-0000-0000-0000-000000000001");

    @Test
    @DisplayName("createGlobalUser - bcrypt hash ile SUPER_ADMIN kaydı")
    void createGlobalUser_happyPath() {
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(jdbcTemplate.update(contains("public.global_users"), any(), any(), any(), any()))
                .thenReturn(1);

        GlobalUserSummary summary = service.createGlobalUser(
                new CreateGlobalUserRequest("root", "Platform!123", "root@learniq.io"));

        assertThat(summary.id()).isNotNull();
        assertThat(summary.username()).isEqualTo("root");
        verify(jdbcTemplate).update(contains("public.global_users"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("createGlobalUser - eksik alan → 400, DB'ye hiç dokunulmaz")
    void createGlobalUser_missingFields_badRequest() {
        assertThatThrownBy(() -> service.createGlobalUser(new CreateGlobalUserRequest(null, "Platform!123", "x@y.z")))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("createGlobalUser - kısa şifre → 400")
    void createGlobalUser_shortPassword_badRequest() {
        assertThatThrownBy(() -> service.createGlobalUser(new CreateGlobalUserRequest("root", "kısa", "x@y.z")))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("createGlobalUser - username/email çakışması → 409")
    void createGlobalUser_duplicate_conflict() {
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(jdbcTemplate.update(contains("public.global_users"), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("unique"));

        assertThatThrownBy(() -> service.createGlobalUser(
                new CreateGlobalUserRequest("root", "Platform!123", "root@learniq.io")))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("resetPassword - hash güncellenir")
    void resetPassword_updates() {
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(jdbcTemplate.update(eq("UPDATE public.global_users SET password_hash = ? WHERE id = ?"),
                eq("hashed"), eq(TARGET)))
                .thenReturn(1);

        service.resetPassword(TARGET, "YenSifre1234");

        verify(jdbcTemplate).update(eq("UPDATE public.global_users SET password_hash = ? WHERE id = ?"),
                eq("hashed"), eq(TARGET));
    }

    @Test
    @DisplayName("resetPassword - global user yoksa 404")
    void resetPassword_notFound() {
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(jdbcTemplate.update(eq("UPDATE public.global_users SET password_hash = ? WHERE id = ?"),
                eq("hashed"), eq(TARGET)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.resetPassword(TARGET, "YenSifre1234"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("listGlobalUsers - search ILIKE + sayfalama")
    void listGlobalUsers() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any())).thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any(), any()))
                .thenReturn(List.of(new GlobalUserSummary(TARGET, "root", "root@x", null)));

        GlobalUserListResponse resp = service.listGlobalUsers("root", 0, 20);

        assertThat(resp.totalElements()).isEqualTo(1);
        assertThat(resp.content()).hasSize(1);
    }
}
