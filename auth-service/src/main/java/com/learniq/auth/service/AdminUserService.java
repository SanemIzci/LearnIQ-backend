package com.learniq.auth.service;

import com.learniq.auth.dto.AdminUserSummary;
import com.learniq.auth.dto.PatchUserRequest;
import com.learniq.auth.dto.UserListResponse;
import com.learniq.common.tenant.TenantSchemaValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ADMIN user lifecycle (Faz C). Tüm SQL'ler şema-niteliklidir (tenant.users) —
 * SET search_path'e güvenilmez (havuz bağlantıları arasında aşağı sızar).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public UserListResponse listUsers(String tenantId, String search, String role, int page, int size) {
        String schema = schema(tenantId);

        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> params = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            where.append(" AND (username ILIKE ? OR email ILIKE ? OR full_name ILIKE ?) ");
            params.addAll(List.of(like(search), like(search), like(search)));
        }
        if (role != null && !role.isBlank()) {
            String upper = role.toUpperCase();
            if (!List.of("ADMIN", "TEACHER", "STUDENT").contains(upper)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be one of: ADMIN, TEACHER, STUDENT");
            }
            where.append(" AND role = ? ");
            params.add(upper);
        }

        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + schema + ".users u" + where, Long.class, params.toArray());

        List<AdminUserSummary> content = jdbcTemplate.query(
                "SELECT id, username, email, full_name, role, active, created_at "
                        + "FROM " + schema + ".users u" + where
                        + " ORDER BY u.created_at DESC, u.username LIMIT ? OFFSET ?",
                (rs, i) -> new AdminUserSummary(
                        (UUID) rs.getObject("id"),
                        rs.getString("username"),
                        rs.getString("email"),
                        rs.getString("full_name"),
                        rs.getString("role"),
                        rs.getBoolean("active"),
                        toLocalDateTime(rs.getTimestamp("created_at"))),
                paramsToArray(params, size, page));

        return new UserListResponse(total, page, size, content);
    }

    @Transactional(readOnly = true)
    public AdminUserSummary getUserDetail(String tenantId, UUID userId) {
        String schema = schema(tenantId);
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id, username, email, full_name, role, active, created_at FROM %s.users WHERE id = ?"
                            .formatted(schema),
                    (rs, i) -> new AdminUserSummary(
                            (UUID) rs.getObject("id"),
                            rs.getString("username"),
                            rs.getString("email"),
                            rs.getString("full_name"),
                            rs.getString("role"),
                            rs.getBoolean("active"),
                            toLocalDateTime(rs.getTimestamp("created_at"))),
                    userId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId);
        }
    }

    /**
     * PATCH: active/fullName/email. En az bir alan zorunlu; kendi hesabının pasifleştirilmesi yasak.
     * Pasifleştirme, kullanıcının aktif refresh token'larını da geçersizleştirir.
     */
    @Transactional
    public AdminUserSummary updateUser(String tenantId, UUID requesterId, UUID targetUserId, PatchUserRequest req) {
        if (req == null || (req.active() == null && req.fullName() == null && req.email() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one field (active, fullName, email) must be provided");
        }
        if (req.active() != null && Boolean.FALSE.equals(req.active()) && requesterId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot deactivate your own account");
        }

        String schema = schema(tenantId);
        AdminUserSummary current = getUserDetail(tenantId, targetUserId);

        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (req.fullName() != null) {
            sets.add("full_name = ?");
            args.add(req.fullName());
        }
        if (req.email() != null) {
            if (req.email().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email must not be blank");
            }
            sets.add("email = ?");
            args.add(req.email());
        }
        if (req.active() != null) {
            sets.add("active = ?");
            args.add(req.active());
        }
        args.add(targetUserId);

        try {
            jdbcTemplate.update(
                    "UPDATE %s.users SET %s WHERE id = ?".formatted(schema, String.join(", ", sets)),
                    args.toArray());
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }

        if (req.active() != null) {
            jdbcTemplate.update(
                    "UPDATE %s.refresh_tokens SET revoked = TRUE WHERE user_id = ? AND revoked = FALSE".formatted(schema),
                    targetUserId);
            log.info("User '{}' set active={} in tenant '{}'; refresh tokens revoked", targetUserId, req.active(), tenantId);
        }

        return new AdminUserSummary(current.id(), current.username(),
                req.email() != null ? req.email() : current.email(),
                req.fullName() != null ? req.fullName() : current.fullName(),
                current.role(), req.active() != null ? req.active() : current.active(), current.createdAt());
    }

    /**
     * ADMIN reset-password: hash güncellenir ve tüm refresh token'lar iptal edilir.
     */
    @Transactional
    public void resetPassword(String tenantId, UUID targetUserId, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        String schema = schema(tenantId);
        AdminUserSummary current = getUserDetail(tenantId, targetUserId);
        String hash = passwordEncoder.encode(newPassword);
        jdbcTemplate.update(
                "UPDATE %s.users SET password_hash = ? WHERE id = ?".formatted(schema), hash, targetUserId);
        jdbcTemplate.update(
                "UPDATE %s.refresh_tokens SET revoked = TRUE WHERE user_id = ? AND revoked = FALSE".formatted(schema),
                targetUserId);
        log.info("Password reset by admin for user '{}' in tenant '{}'", current.username(), tenantId);
    }

    private String schema(String tenantId) {
        return TenantSchemaValidator.requireValid(tenantId);
    }

    private String like(String value) {
        return "%" + value.trim() + "%";
    }

    private Object[] paramsToArray(List<Object> params, int size, int page) {
        List<Object> all = new ArrayList<>(params);
        all.add(size);
        all.add((long) size * page);
        return all.toArray();
    }

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }
}
