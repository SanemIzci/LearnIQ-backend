package com.learniq.auth.controller;

import com.learniq.auth.dto.AdminUserSummary;
import com.learniq.auth.dto.PatchUserRequest;
import com.learniq.auth.dto.ResetPasswordRequest;
import com.learniq.auth.dto.UserListResponse;
import com.learniq.auth.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * ADMIN user lifecycle uçları (Faz C). Fail-closed: ADMIN | SUPER_ADMIN dışı roller 403.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    private void requirePlatformAdmin(String role) {
        if (!"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    @GetMapping
    public ResponseEntity<UserListResponse> listUsers(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "role", required = false) String roleFilter,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size) {

        requirePlatformAdmin(role);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return ResponseEntity.ok(
                adminUserService.listUsers(tenantId, search, roleFilter, safePage, safeSize));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminUserSummary> getUserDetail(
            @PathVariable UUID id,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Role") String role) {

        requirePlatformAdmin(role);
        return ResponseEntity.ok(adminUserService.getUserDetail(tenantId, id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<AdminUserSummary> updateUser(
            @PathVariable UUID id,
            @RequestBody PatchUserRequest req,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role) {

        requirePlatformAdmin(role);
        return ResponseEntity.ok(
                adminUserService.updateUser(tenantId, UUID.fromString(userId), id, req));
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<java.util.Map<String, Object>> resetPassword(
            @PathVariable UUID id,
            @RequestBody ResetPasswordRequest req,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Role") String role) {

        requirePlatformAdmin(role);
        adminUserService.resetPassword(tenantId, id, req.newPassword());
        return ResponseEntity.ok(java.util.Map.of("reset", true));
    }
}
