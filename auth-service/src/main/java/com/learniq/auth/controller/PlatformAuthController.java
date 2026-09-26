package com.learniq.auth.controller;

import com.learniq.auth.dto.AuthResponse;
import com.learniq.auth.service.JwtService;
import com.learniq.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Platform-level authentication for SUPER_ADMIN only.
 *
 * Completely separate from the tenant auth flow:
 *   - No tenantId required
 *   - Looks up public.global_users
 *   - Issues a short-lived access token (15 min) with no tenantId claim
 *   - Role in JWT: SUPER_ADMIN
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/platform/auth")
@RequiredArgsConstructor
public class PlatformAuthController {

    private final JwtService jwtService;
    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || password == null) {
            return ResponseEntity.badRequest().build();
        }

        // Look up in public.global_users — no tenant schema involved
        Map<String, Object> user = userService.findGlobalUser(username);

        if (user == null) {
            log.warn("Platform login failed: user '{}' not found in global_users", username);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!userService.verifyPassword(password, (String) user.get("password_hash"))) {
            log.warn("Platform login failed: wrong password for '{}'", username);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID userId = UUID.fromString(user.get("id").toString());
        String role  = (String) user.get("role"); // "SUPER_ADMIN"

        // Short-lived access token (15 min), no tenantId
        String accessToken  = jwtService.generateSuperAdminToken(userId, username);
        // No refresh token stored in DB — SUPER_ADMIN sessions are short by design
        String refreshToken = jwtService.generateRefreshToken(userId, "platform");

        log.info("SUPER_ADMIN '{}' logged in successfully", username);
        return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken, role, userId.toString(), username));
    }
}
