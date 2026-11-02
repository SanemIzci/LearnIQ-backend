package com.learniq.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantSubscriptionService Enforcement Tests (Karar 3/C)")
class TenantSubscriptionServiceTest {

    private final String SQL = "SELECT subscription_status FROM public.tenants WHERE id = ?";

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("ACTIVE tenant → erişim onaylanır")
    void activeTenant_passes() {
        when(jdbcTemplate.queryForObject(contains("subscription_status"), eq(String.class), eq("akademi_1")))
                .thenReturn("ACTIVE");

        new TenantSubscriptionService(jdbcTemplate).requireActiveTenant("akademi_1");
    }

    @Test
    @DisplayName("SUSPENDED/EXPIRED tenant → 403")
    void suspendedTenant_forbidden() {
        TenantSubscriptionService svc = new TenantSubscriptionService(jdbcTemplate);
        when(jdbcTemplate.queryForObject(contains("subscription_status"), eq(String.class), eq("akademi_1")))
                .thenReturn("SUSPENDED");

        assertThatThrownBy(() -> svc.requireActiveTenant("akademi_1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);

        assertThatThrownBy(() -> svc.requireActiveTenant("akademi_1"))
                .hasMessageContaining("SUSPENDED");
    }

    @Test
    @DisplayName("kayıtsız tenant → 401")
    void unknownTenant_unauthorized() {
        when(jdbcTemplate.queryForObject(contains("subscription_status"), eq(String.class), eq("ghost")))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThatThrownBy(() -> new TenantSubscriptionService(jdbcTemplate).requireActiveTenant("ghost"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("boş tenant id → 401, sorgu yok")
    void blankTenant_unauthorized() {
        assertThatThrownBy(() -> new TenantSubscriptionService(jdbcTemplate).requireActiveTenant(" "))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(jdbcTemplate);
    }
}
