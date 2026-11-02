package com.learniq.auth.controller;

import com.learniq.auth.dto.*;
import com.learniq.auth.service.GlobalUserService;
import com.learniq.auth.service.TenantProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Faz B — SUPER_ADMIN platform yönetimi.
 * Fail-closed: SUPER_ADMIN dışındaki roller 403 (rol header'ı yoksa 400).
 * Endpoint'ler tenant-agnostic: X-Tenant-Id istemez.
 *
 * Gateway: /api/v1/platform/admin/** JWT-zorunlu routa bağlıdır;
 * /api/v1/platform/auth/login açık kalır.
 */
@RestController
@RequestMapping("/api/v1/platform/admin")
@RequiredArgsConstructor
public class PlatformAdminController {

    private final TenantProvisioningService tenantProvisioningService;
    private final GlobalUserService globalUserService;

    private void requireSuperAdmin(String role) {
        if (!"SUPER_ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    private int clampPage(int page) {
        return Math.max(page, 0);
    }

    private int clampSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    // ── Tenant yönetimi ──────────────────────────────────────────────────────

    @GetMapping("/tenants")
    public ResponseEntity<TenantListResponse> listTenants(
            @RequestHeader("X-User-Role") String role,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size) {

        requireSuperAdmin(role);
        return ResponseEntity.ok(
                tenantProvisioningService.listTenants(search, status, clampPage(page), clampSize(size)));
    }

    @GetMapping("/tenants/{id}")
    public ResponseEntity<TenantDetail> getTenant(
            @PathVariable String id,
            @RequestHeader("X-User-Role") String role) {

        requireSuperAdmin(role);
        return ResponseEntity.ok(tenantProvisioningService.getTenantDetail(id));
    }

    @PostMapping("/tenants")
    public ResponseEntity<CreateTenantResponse> provisionTenant(
            @RequestBody CreateTenantRequest request,
            @RequestHeader("X-User-Role") String role) {

        requireSuperAdmin(role);
        CreateTenantResponse response = tenantProvisioningService.provisionTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/tenants/{id}/subscription")
    public ResponseEntity<TenantSummary> updateSubscription(
            @PathVariable String id,
            @RequestBody SubscriptionUpdateRequest request,
            @RequestHeader("X-User-Role") String role) {

        requireSuperAdmin(role);
        return ResponseEntity.ok(tenantProvisioningService.updateSubscription(id, request));
    }

    // ── Platform hesap yönetimi ──────────────────────────────────────────────

    @GetMapping("/global-users")
    public ResponseEntity<GlobalUserListResponse> listGlobalUsers(
            @RequestHeader("X-User-Role") String role,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size) {

        requireSuperAdmin(role);
        return ResponseEntity.ok(
                globalUserService.listGlobalUsers(search, clampPage(page), clampSize(size)));
    }

    @PostMapping("/global-users")
    public ResponseEntity<GlobalUserSummary> createGlobalUser(
            @RequestBody CreateGlobalUserRequest request,
            @RequestHeader("X-User-Role") String role) {

        requireSuperAdmin(role);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(globalUserService.createGlobalUser(request));
    }

    @PostMapping("/global-users/{id}/reset-password")
    public ResponseEntity<java.util.Map<String, Object>> resetGlobalUserPassword(
            @PathVariable UUID id,
            @RequestBody ResetPasswordRequest request,
            @RequestHeader("X-User-Role") String role) {

        requireSuperAdmin(role);
        globalUserService.resetPassword(id, request.newPassword());
        return ResponseEntity.ok(java.util.Map.of("reset", true));
    }
}
