package com.learniq.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tenant abonelik enforcement (Faz B): login / register / refresh öncesi
 * public.tenants tablosundan tek satır kontrol edilir.
 * SUSPENDED/EXPIRED tenant'lar anında yeni oturum alamaz; erişim refresh
 * döngüsüyle düşer (gateway'e DB bağlanmaz — Karar 3/C).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantSubscriptionService {

    private final JdbcTemplate jdbcTemplate;

    public void requireActiveTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid tenant");
        }
        String status;
        try {
            status = jdbcTemplate.queryForObject(
                    "SELECT subscription_status FROM public.tenants WHERE id = ?",
                    String.class, tenantId);
        } catch (EmptyResultDataAccessException e) {
            log.warn("Auth blocked: tenant '{}' not registered", tenantId);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid tenant");
        }
        if (!"ACTIVE".equals(status)) {
            log.warn("Auth blocked: tenant '{}' status={}", tenantId, status);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant is " + status);
        }
    }
}
