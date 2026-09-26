package com.learniq.auth.service;

import com.learniq.auth.dto.RegisterRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Handles user lookup and registration using raw JDBC with dynamic schema switching.
 * This avoids wiring up full Hibernate multi-tenancy in auth-service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // ---- User Lookup ----

    public Map<String, Object> findUserByUsername(String tenantId, String username) {
        setSchema(tenantId);
        var rows = jdbcTemplate.queryForList(
                "SELECT id, username, password_hash, role, active FROM users WHERE username = ?",
                username);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> findUserById(String tenantId, UUID userId) {
        setSchema(tenantId);
        var rows = jdbcTemplate.queryForList(
                "SELECT id, username, email, full_name, role FROM users WHERE id = ?",
                userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ---- Password Verification ----

    public boolean verifyPassword(String rawPassword, String hash) {
        return passwordEncoder.matches(rawPassword, hash);
    }

    // ---- User Registration ----

    public UUID registerUser(String tenantId, RegisterRequest req) {
        setSchema(tenantId);
        UUID id = UUID.randomUUID();
        String hash = passwordEncoder.encode(req.getPassword());
        jdbcTemplate.update(
                "INSERT INTO users (id, username, password_hash, email, full_name, role) VALUES (?, ?, ?, ?, ?, ?)",
                id, req.getUsername(), hash, req.getEmail(), req.getFullName(), req.getRole().toUpperCase());
        log.info("Registered new user '{}' with role '{}' in tenant '{}'", req.getUsername(), req.getRole(), tenantId);
        return id;
    }

    // ---- Refresh Token Management ----

    public void saveRefreshToken(String tenantId, UUID userId, String token) {
        setSchema(tenantId);
        Timestamp expiresAt = Timestamp.from(Instant.now().plus(7, ChronoUnit.DAYS));
        jdbcTemplate.update(
                "INSERT INTO refresh_tokens (user_id, token, expires_at) VALUES (?, ?, ?)",
                userId, token, expiresAt);
    }

    public Map<String, Object> findRefreshToken(String tenantId, String token) {
        setSchema(tenantId);
        var rows = jdbcTemplate.queryForList(
                "SELECT rt.*, u.username, u.role FROM refresh_tokens rt " +
                "JOIN users u ON u.id = rt.user_id " +
                "WHERE rt.token = ? AND rt.revoked = FALSE AND rt.expires_at > NOW()",
                token);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void revokeRefreshToken(String tenantId, String token) {
        setSchema(tenantId);
        jdbcTemplate.update("UPDATE refresh_tokens SET revoked = TRUE WHERE token = ?", token);
    }

    // ---- Schema Switcher ----

    private void setSchema(String tenantId) {
        jdbcTemplate.execute("SET search_path TO " + tenantId);
    }
}
