package com.learniq.auth.service;

import com.learniq.auth.dto.CreateGlobalUserRequest;
import com.learniq.auth.dto.GlobalUserSummary;
import com.learniq.auth.dto.GlobalUserListResponse;
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
import java.util.UUID;

/**
 * SUPER_ADMIN platform hesapları (public.global_users).
 * Rol sabit SUPER_ADMIN — whitelist yok; hesap silme yok (audit izi korunur).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalUserService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public GlobalUserListResponse listGlobalUsers(String search, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> params = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            where.append(" AND (username ILIKE ? OR email ILIKE ?) ");
            String like = "%" + search.trim() + "%";
            params.addAll(List.of(like, like));
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.global_users u" + where, Long.class, params.toArray());
        List<GlobalUserSummary> content = jdbcTemplate.query(
                "SELECT id, username, email, created_at FROM public.global_users u" + where
                        + " ORDER BY u.created_at DESC, u.username LIMIT ? OFFSET ?",
                (rs, i) -> new GlobalUserSummary(
                        (UUID) rs.getObject("id"),
                        rs.getString("username"),
                        rs.getString("email"),
                        toLocal(rs.getTimestamp("created_at"))),
                joinParams(params, size, page));

        return new GlobalUserListResponse(total != null ? total : 0, page, size, content);
    }

    @Transactional
    public GlobalUserSummary createGlobalUser(CreateGlobalUserRequest req) {
        if (req == null || isBlank(req.username()) || isBlank(req.password()) || isBlank(req.email())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "username, password and email are required");
        }
        if (req.password().length() < MIN_PASSWORD_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        UUID id = UUID.randomUUID();
        try {
            jdbcTemplate.update(
                    "INSERT INTO public.global_users (id, username, password_hash, email, role) "
                            + "VALUES (?, ?, ?, ?, 'SUPER_ADMIN')",
                    id, req.username(), passwordEncoder.encode(req.password()), req.email());
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "username or email already exists");
        }
        log.info("New SUPER_ADMIN '{}' created", req.username());
        return new GlobalUserSummary(id, req.username(), req.email(), LocalDateTime.now());
    }

    @Transactional
    public void resetPassword(UUID targetId, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        int updated = jdbcTemplate.update(
                "UPDATE public.global_users SET password_hash = ? WHERE id = ?",
                passwordEncoder.encode(newPassword), targetId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Global user not found: " + targetId);
        }
        log.info("SUPER_ADMIN password reset for global user '{}'", targetId);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Object[] joinParams(List<Object> params, int size, int page) {
        List<Object> all = new ArrayList<>(params);
        all.add(size);
        all.add((long) size * page);
        return all.toArray();
    }

    private LocalDateTime toLocal(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }
}
