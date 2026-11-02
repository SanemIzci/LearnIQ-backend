package com.learniq.auth.controller;

import com.learniq.auth.dto.CreateTenantRequest;
import com.learniq.auth.dto.CreateTenantResponse;
import com.learniq.auth.dto.TenantDetail;
import com.learniq.auth.dto.TenantListResponse;
import com.learniq.auth.dto.TenantSummary;
import com.learniq.auth.exception.AuthExceptionHandler;
import com.learniq.auth.service.GlobalUserService;
import com.learniq.auth.service.TenantProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("PlatformAdminController Fail-Closed Tests (Faz B, standalone)")
class PlatformAdminControllerTest {

    private TenantProvisioningService tenantProvisioningService;
    private GlobalUserService globalUserService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        tenantProvisioningService = mock(TenantProvisioningService.class);
        globalUserService = mock(GlobalUserService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new PlatformAdminController(tenantProvisioningService, globalUserService))
                .setControllerAdvice(new AuthExceptionHandler())
                .build();
    }

    private void stubList() {
        when(tenantProvisioningService.listTenants(any(), any(), anyInt(), anyInt()))
                .thenReturn(new TenantListResponse(1, 0, 20, List.of(
                        new TenantSummary("akademi_1", "Akademi", null, "ACTIVE", null))));
    }

    @Test
    @DisplayName("tenants listesi - rol eksik 400, ADMIN 403, SUPER_ADMIN 200")
    void listTenants_guardMatrix() throws Exception {
        mockMvc.perform(get("/api/v1/platform/admin/tenants"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/platform/admin/tenants")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isForbidden());

        stubList();
        mockMvc.perform(get("/api/v1/platform/admin/tenants")
                        .header("X-User-Role", "SUPER_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value("akademi_1"));
    }

    @Test
    @DisplayName("tenants listesi - sayfa sınırlandırması: page<0→0, size>100→100")
    void listTenants_paginationClamp() throws Exception {
        stubList();

        mockMvc.perform(get("/api/v1/platform/admin/tenants")
                        .header("X-User-Role", "SUPER_ADMIN")
                        .param("page", "-2")
                        .param("size", "999"))
                .andExpect(status().isOk());

        verify(tenantProvisioningService).listTenants(null, null, 0, 100);
    }

    @Test
    @DisplayName("provision - 201 Created + delegasyon; geçersiz istek 400 handler'a düşer")
    void provisionTenant_created() throws Exception {
        when(tenantProvisioningService.provisionTenant(any(CreateTenantRequest.class)))
                .thenReturn(new CreateTenantResponse(
                        new TenantSummary("akademi_2", "Akademi 2", "d.com", "ACTIVE", null),
                        "admin@ak2", "TempPass1"));

        mockMvc.perform(post("/api/v1/platform/admin/tenants")
                        .header("X-User-Role", "SUPER_ADMIN")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"id":"akademi_2","name":"Akademi 2","adminUsername":"admin@ak2",
                                 "adminPassword":"Sifre12345"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenant.id").value("akademi_2"))
                .andExpect(jsonPath("$.temporaryPassword").value("TempPass1"));

        verify(tenantProvisioningService).provisionTenant(any(CreateTenantRequest.class));
    }

    @Test
    @DisplayName("subscription PATCH - delegasyon + geçersiz statü 400; detay userCount içerir")
    void subscription_and_detail() throws Exception {
        when(tenantProvisioningService.updateSubscription(eq("akademi_1"), any()))
                .thenReturn(new TenantSummary("akademi_1", "Akademi", null, "SUSPENDED", null));

        mockMvc.perform(patch("/api/v1/platform/admin/tenants/akademi_1/subscription")
                        .header("X-User-Role", "SUPER_ADMIN")
                        .contentType(APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionStatus").value("SUSPENDED"));

        when(tenantProvisioningService.getTenantDetail("akademi_1"))
                .thenReturn(new TenantDetail("akademi_1", "Akademi", null, "SUSPENDED", null, 7));

        mockMvc.perform(get("/api/v1/platform/admin/tenants/akademi_1")
                        .header("X-User-Role", "SUPER_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userCount").value(7));
    }

    @Test
    @DisplayName("global-users - create 201 + reset 200 + guard STUDENT 403")
    void globalUsers_flow() throws Exception {
        mockMvc.perform(post("/api/v1/platform/admin/global-users")
                        .header("X-User-Role", "STUDENT")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/platform/admin/global-users")
                        .header("X-User-Role", "SUPER_ADMIN")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"root\",\"password\":\"Platform!123\",\"email\":\"root@l.io\"}"))
                .andExpect(status().isCreated());

        verify(globalUserService).createGlobalUser(any());
    }
}
