package com.learniq.auth.service;

import com.learniq.auth.dto.AdminUserSummary;
import com.learniq.auth.dto.PatchUserRequest;
import com.learniq.auth.dto.UserListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
@DisplayName("AdminUserService Unit Tests (şema-nitelikli)")
class AdminUserServiceTest {

    private static final String TENANT = "akademi_1";
    private static final UUID REQUESTER = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("cccccccc-0000-0000-0000-000000000001");

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminUserService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(jdbcTemplate, passwordEncoder);
    }

    private AdminUserSummary summary() {
        return new AdminUserSummary(TARGET, "ayse", "ayse@x.com", "Ayşe Demir",
                "STUDENT", true, null);
    }

    @Test
    @DisplayName("listUsers - şema-nitelikli SQL (SET search_path yok) + sayfalama argümanları")
    void listUsers_schemaQualified() {
        when(jdbcTemplate.queryForObject(contains(TENANT + ".users"), eq(Long.class), new Object[0]))
                .thenReturn(1L);
        when(jdbcTemplate.query(contains(TENANT + ".users"), any(RowMapper.class), any(), any()))
                .thenReturn(List.of(summary()));

        UserListResponse resp = service.listUsers(TENANT, null, null, 0, 20);

        assertThat(resp.totalElements()).isEqualTo(1);
        assertThat(resp.content()).hasSize(1);
        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    @DisplayName("listUsers - search ILIKE filtresi üç kolona da uygulanır")
    void listUsers_searchFilter() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any(), any()))
                .thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        service.listUsers(TENANT, "ayşe", null, 0, 20);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), any(), any(), any(), any(), any());
        assertThat(sql.getValue()).contains("username ILIKE ?").contains("email ILIKE ?").contains("full_name ILIKE ?");
    }

    @Test
    @DisplayName("listUsers - rol filtresi whitelist dışıysa 400")
    void listUsers_invalidRoleFilter_badRequest() {
        assertThatThrownBy(() -> service.listUsers(TENANT, null, "HACKER", 0, 20))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("listUsers - geçersiz şema → IllegalArgumentException, sorgu YOK")
    void listUsers_invalidTenant_rejected() {
        assertThatThrownBy(() -> service.listUsers("Public;DROP", null, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("getUserDetail - kullanıcı yoksa 404")
    void getUserDetail_notFound() {
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(TARGET)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThatThrownBy(() -> service.getUserDetail(TENANT, TARGET))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("updateUser - boş gövde (tüm alanlar null) → 400")
    void updateUser_emptyBody_badRequest() {
        assertThatThrownBy(() -> service.updateUser(TENANT, REQUESTER, TARGET, new PatchUserRequest(null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("updateUser - kendi hesabını pasifleştirme → 400")
    void updateUser_selfDeactivate_badRequest() {
        assertThatThrownBy(() -> service.updateUser(TENANT, TARGET, TARGET, new PatchUserRequest(false, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("updateUser - pasifleştirme → users UPDATE + refresh_tokens iptali")
    void updateUser_deactivate_revokesTokens() {
        when(jdbcTemplate.queryForObject(contains(".users WHERE id = ?"), any(RowMapper.class), eq(TARGET)))
                .thenReturn(summary());

        AdminUserSummary updated = service.updateUser(TENANT, REQUESTER, TARGET,
                new PatchUserRequest(false, "Yeni İsim", null));

        verify(jdbcTemplate).update(contains(TENANT + ".users"), eq("Yeni İsim"), eq(false), eq(TARGET));
        verify(jdbcTemplate).update(contains(TENANT + ".refresh_tokens"), eq(TARGET));
        assertThat(updated.active()).isFalse();
        assertThat(updated.fullName()).isEqualTo("Yeni İsim");
    }

    @Test
    @DisplayName("updateUser - e-posta eşsizlik ihlali → 409")
    void updateUser_duplicateEmail_conflict() {
        when(jdbcTemplate.queryForObject(contains(".users WHERE id = ?"), any(RowMapper.class), eq(TARGET)))
                .thenReturn(summary());
        when(jdbcTemplate.update(contains(TENANT + ".users"), any(Object[].class)))
                .thenThrow(new DataIntegrityViolationException("unique"));

        assertThatThrownBy(() -> service.updateUser(TENANT, REQUESTER, TARGET,
                new PatchUserRequest(null, null, "taken@x.com")))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("resetPassword - hash güncellenir + tüm refresh token'lar iptal edilir")
    void resetPassword_updatesAndRevokes() {
        when(jdbcTemplate.queryForObject(contains(".users WHERE id = ?"), any(RowMapper.class), eq(TARGET)))
                .thenReturn(summary());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");

        service.resetPassword(TENANT, TARGET, "YeniSifre123");

        ArgumentCaptor<String> hashArg = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(contains(TENANT + ".users"), hashArg.capture(), eq(TARGET));
        assertThat(hashArg.getValue()).isEqualTo("hashed");
        verify(jdbcTemplate).update(contains(TENANT + ".refresh_tokens"), eq(TARGET));
    }

    @Test
    @DisplayName("resetPassword - kısa şifre → 400, DB'ye hiç dokunulmaz")
    void resetPassword_tooShort_badRequest() {
        assertThatThrownBy(() -> service.resetPassword(TENANT, TARGET, "kısa"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

        verifyNoInteractions(jdbcTemplate);
    }
}
