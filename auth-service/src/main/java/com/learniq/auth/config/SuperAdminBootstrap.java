package com.learniq.auth.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Secure bootstrap for the first SUPER_ADMIN (platform user).
 *
 * Rules:
 * - Runs only when `platform.super-admin.enabled=true` (default true).
 * - Creates a row in public.global_users ONLY if the table is empty;
 *   existing users are never touched.
 * - Password comes from `platform.super-admin.password`; when unset a random
 *   12+ char password is generated and printed ONCE (server start only).
 * - Registration remains ADMIN-only: platform users are never created through
 *   any public endpoint.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAdminBootstrap implements ApplicationRunner {

    private final JdbcOperations jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Value("${platform.super-admin.enabled:true}")
    private boolean enabled;

    @Value("${platform.super-admin.username:superadmin}")
    private String username;

    @Value("${platform.super-admin.password:}")
    private String configuredPassword;

    @Override
    public void run(ApplicationArguments args) {
        try {
            bootstrap();
        } catch (Exception e) {
            log.error("SUPER_ADMIN bootstrap failed (service continues to run): {}", e.getMessage());
        }
    }

    void bootstrap() {
        if (!enabled) {
            log.info("SUPER_ADMIN bootstrap disabled by configuration");
            return;
        }

        Long existing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.global_users", Long.class);
        if (existing != null && existing > 0) {
            log.info("Bootstrap skipped: public.global_users already has {} platform user(s)", existing);
            return;
        }

        String password = configuredPassword != null && !configuredPassword.isBlank()
                ? configuredPassword
                : generateRandomPassword();
        boolean generated = configuredPassword == null || configuredPassword.isBlank();

        jdbcTemplate.update(
                "INSERT INTO public.global_users (id, username, password_hash, email, role) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                username,
                passwordEncoder.encode(password),
                username + "@superadmin.learniq.local",
                "SUPER_ADMIN");

        if (generated) {
            log.warn("Initial SUPER_ADMIN '{}' created with a generated password (change it after first login): {}",
                    username, password);
        } else {
            log.info("Initial SUPER_ADMIN '{}' created using the configured bootstrap password", username);
        }
    }

    private String generateRandomPassword() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, 12);
    }
}
