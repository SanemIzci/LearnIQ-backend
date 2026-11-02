package com.learniq.auth.service;

import com.learniq.auth.dto.CreateTenantRequest;
import com.learniq.auth.dto.CreateTenantResponse;
import com.learniq.auth.dto.SubscriptionUpdateRequest;
import com.learniq.auth.dto.TenantListResponse;
import com.learniq.auth.dto.TenantSummary;
import com.learniq.common.tenant.flyway.TenantSchemaMigrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantProvisioningService Unit Tests (Faz B)")
class TenantProvisioningServiceTest {

    private static final String TENANT = "akademi_2";

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private DataSource dataSource;

    private TenantProvisioningService service() {
        return new TenantProvisioningService(jdbcTemplate, passwordEncoder, dataSource);
    }

    private CreateTenantRequest request(String id, String domain) {
        return new CreateTenantRequest(id, "Akademi 2", domain, "admin@ak2", "admin@ak2.com", "Admin", "Sifre12345");
    }

    @Test
    @DisplayName("provisionTenant - INSERT→CREATE SCHEMA→Flyway→admin user sırayla koşar")
    void provision_happyPath() {
        try (MockedStatic<TenantSchemaMigrator> migrator = mockStatic(TenantSchemaMigrator.class)) {
            when(jdbcTemplate.queryForObject(contains("public.tenants WHERE id = ?"), eq(Long.class), eq(TENANT)))
                    .thenReturn(0L);
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            doNothing().when(jdbcTemplate).execute(contains("CREATE SCHEMA IF NOT EXISTS " + TENANT));
            when(jdbcTemplate.update(contains("INSERT INTO public.tenants"), any(), any(), any())).thenReturn(1);
            when(jdbcTemplate.update(contains(TENANT + ".users"), any(), any(), any(), any(), any())).thenReturn(1);

            CreateTenantResponse resp = service().provisionTenant(request(TENANT, null));

            assertThat(resp.tenant().id()).isEqualTo(TENANT);
            assertThat(resp.tenant().subscriptionStatus()).isEqualTo("ACTIVE");
            assertThat(resp.temporaryPassword()).isNull();

            InOrder order = inOrder(jdbcTemplate);
            order.verify(jdbcTemplate).update(contains("INSERT INTO public.tenants"), eq(TENANT), any(), any());
            order.verify(jdbcTemplate).execute(contains("CREATE SCHEMA IF NOT EXISTS " + TENANT));
            order.verify(jdbcTemplate).update(contains(TENANT + ".users"), any(), any(), any(), any(), any());

            migrator.verify(() -> TenantSchemaMigrator.migrateTenant(dataSource, TENANT));
        }
    }

    @Test
    @DisplayName("provisionTenant - adminPassword verilmezse üretilir ve yanıtta gösterilir")
    void provision_generatedPassword() {
        try (MockedStatic<TenantSchemaMigrator> ignored = mockStatic(TenantSchemaMigrator.class)) {
            when(jdbcTemplate.queryForObject(contains("public.tenants WHERE id = ?"), eq(Long.class), eq(TENANT)))
                    .thenReturn(0L);
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            doNothing().when(jdbcTemplate).execute(anyString());
            when(jdbcTemplate.update(contains("INSERT INTO public.tenants"), any(), any(), any())).thenReturn(1);
            when(jdbcTemplate.update(contains(TENANT + ".users"), any(), any(), any(), any(), any())).thenReturn(1);

            CreateTenantRequest req = new CreateTenantRequest(TENANT, "Akademi 2", "d.com",
                    "admin@ak2", null, null, null);
            CreateTenantResponse resp = service().provisionTenant(req);

            assertThat(resp.temporaryPassword()).isNotBlank();
        }
    }

    @Test
    @DisplayName("provisionTenant - aynı tenant id → 409, şema açılmaz")
    void provision_duplicateId_conflict() {
        when(jdbcTemplate.queryForObject(contains("public.tenants WHERE id = ?"), eq(Long.class), eq(TENANT)))
                .thenReturn(1L);

        assertThatThrownBy(() -> service().provisionTenant(request(TENANT, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);

        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    @DisplayName("provisionTenant - geçersiz şema adı → 400, hiçbir yazım yok")
    void provision_invalidSchemaId_badRequest() {
        assertThatThrownBy(() -> service().provisionTenant(request("Bad Id!", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("provisionTenant - Flyway patlayınca şema CASCADE drop + tenants kaydı silinir; 500 döner")
    void provision_migrationFailure_cleanupAnd500() {
        try (MockedStatic<TenantSchemaMigrator> migrator = mockStatic(TenantSchemaMigrator.class)) {
            when(jdbcTemplate.queryForObject(contains("public.tenants WHERE id = ?"), eq(Long.class), eq(TENANT)))
                    .thenReturn(0L);
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            doNothing().when(jdbcTemplate).execute(anyString());
            migrator.when(() -> TenantSchemaMigrator.migrateTenant(any(), any()))
                    .thenThrow(new IllegalStateException("V5 boom"));

            assertThatThrownBy(() -> service().provisionTenant(request(TENANT, null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasFieldOrPropertyWithValue("statusCode", HttpStatus.INTERNAL_SERVER_ERROR);

            verify(jdbcTemplate).execute(contains("DROP SCHEMA IF EXISTS " + TENANT + " CASCADE"));
            verify(jdbcTemplate).update(contains("DELETE FROM public.tenants"), eq(TENANT));
            verify(jdbcTemplate, never()).update(contains(TENANT + ".users"), any(), any(), any(), any(), any());
        }
    }

    @Test
    @DisplayName("updateSubscription - whitelist dışı statu → 400")
    void updateSubscription_invalidStatus_badRequest() {
        assertThatThrownBy(() -> service().updateSubscription(TENANT, new SubscriptionUpdateRequest("PAUSED")))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("updateSubscription - SUSPENDED set edilir; tenant yoksa 404")
    void updateSubscription_suspend_andNotFound() {
        when(jdbcTemplate.queryForObject(contains("FROM public.tenants WHERE id = ?"), any(RowMapper.class), eq(TENANT)))
                .thenReturn(new TenantSummary(TENANT, "A", null, "ACTIVE", null));

        TenantSummary updated = service().updateSubscription(TENANT, new SubscriptionUpdateRequest("suspended"));

        assertThat(updated.subscriptionStatus()).isEqualTo("SUSPENDED");
        verify(jdbcTemplate).update(contains("SET subscription_status"), eq("SUSPENDED"), eq(TENANT));

        when(jdbcTemplate.queryForObject(contains("FROM public.tenants WHERE id = ?"), any(RowMapper.class), eq("ghost")))
                .thenThrow(new EmptyResultDataAccessException(1));
        assertThatThrownBy(() -> service().updateSubscription("ghost", new SubscriptionUpdateRequest("ACTIVE")))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("listTenants - statu filtresi + sayfalama; details şema-nitelikli user count")
    void listTenants_and_detail() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any(), any())).thenReturn(2L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new TenantSummary(TENANT, "A", "d.com", "ACTIVE", null)));

        TenantListResponse resp = service().listTenants("aka", "ACTIVE", 0, 20);

        assertThat(resp.totalElements()).isEqualTo(2);
        assertThat(resp.content()).hasSize(1);

        // detail: tenant satırı + şema-nitelikli user count
        when(jdbcTemplate.queryForObject(contains("FROM public.tenants WHERE id = ?"), any(RowMapper.class), eq(TENANT)))
                .thenReturn(new TenantSummary(TENANT, "A", null, "ACTIVE", null));
        when(jdbcTemplate.queryForObject(contains(TENANT + ".users"), eq(Long.class)))
                .thenReturn(12L);

        var detail = service().getTenantDetail(TENANT);
        assertThat(detail.userCount()).isEqualTo(12);
    }

    @Test
    @DisplayName("listTenants - geçersiz statu filtresi → 400, sorgu yok")
    void listTenants_invalidStatus_badRequest() {
        assertThatThrownBy(() -> service().listTenants(null, "HACKED", 0, 20))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

        verifyNoInteractions(jdbcTemplate);
    }
}
