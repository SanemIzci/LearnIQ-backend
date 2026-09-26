package com.learniq.auth.controller;

import com.learniq.auth.dto.AuthResponse;
import com.learniq.auth.dto.LoginRequest;
import com.learniq.auth.dto.RegisterRequest;
import com.learniq.auth.service.JwtService;
import com.learniq.auth.service.UserService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtService jwtService;
    private final UserService userService;

    // -------------------------------------------------------
    // POST /api/v1/auth/login
    // -------------------------------------------------------
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        Map<String, Object> user = userService.findUserByUsername(request.getTenantId(), request.getUsername());

        if (user == null || !(boolean) user.get("active")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!userService.verifyPassword(request.getPassword(), (String) user.get("password_hash"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID userId = UUID.fromString(user.get("id").toString());
        String role = (String) user.get("role");

        String accessToken  = jwtService.generateAccessToken(userId, request.getUsername(), request.getTenantId(), role);
        String refreshToken = jwtService.generateRefreshToken(userId, request.getTenantId());
        userService.saveRefreshToken(request.getTenantId(), userId, refreshToken);

        log.info("User '{}' (role={}) logged into tenant '{}'", request.getUsername(), role, request.getTenantId());
        return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken, role, userId.toString(), request.getUsername()));
    }

    // -------------------------------------------------------
    // POST /api/v1/auth/register  (ADMIN only — validated via X-User-Role header from gateway)
    // -------------------------------------------------------
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(
            @RequestBody RegisterRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String callerRole) {

        if (!"ADMIN".equals(callerRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Only ADMIN can register new users"));
        }

        UUID newUserId = userService.registerUser(request.getTenantId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("userId", newUserId.toString(), "message", "User created successfully"));
    }

    // -------------------------------------------------------
    // POST /api/v1/auth/refresh
    // -------------------------------------------------------
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody Map<String, String> body) {
        String token = body.get("refreshToken");
        String tenantId = body.get("tenantId");

        if (token == null || tenantId == null) {
            return ResponseEntity.badRequest().build();
        }

        // Validate the JWT signature / expiry first
        Claims claims;
        try {
            claims = jwtService.parseToken(token);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Then check the DB record (revocation check)
        Map<String, Object> record = userService.findRefreshToken(tenantId, token);
        if (record == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Rotate refresh token: revoke old, issue new
        userService.revokeRefreshToken(tenantId, token);

        UUID userId   = UUID.fromString(claims.getSubject());
        String username = (String) record.get("username");
        String role     = (String) record.get("role");

        String newAccess  = jwtService.generateAccessToken(userId, username, tenantId, role);
        String newRefresh = jwtService.generateRefreshToken(userId, tenantId);
        userService.saveRefreshToken(tenantId, userId, newRefresh);

        return ResponseEntity.ok(new AuthResponse(newAccess, newRefresh, role, userId.toString(), username));
    }

    // -------------------------------------------------------
    // GET /api/v1/auth/me  (requires valid JWT, called via gateway)
    // -------------------------------------------------------
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        if (userId == null || tenantId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Map<String, Object> user = userService.findUserById(tenantId, UUID.fromString(userId));
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(user);
    }

    // -------------------------------------------------------
    // POST /api/v1/auth/logout
    // Refresh token'ı iptal eder. Access token 1 saatte doğal olarak biter.
    // -------------------------------------------------------
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {

        String refreshToken = body.get("refreshToken");

        if (refreshToken == null || tenantId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "refreshToken and tenantId are required"));
        }

        userService.revokeRefreshToken(tenantId, refreshToken);
        log.info("User logged out, refresh token revoked for tenant '{}'", tenantId);

        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}
