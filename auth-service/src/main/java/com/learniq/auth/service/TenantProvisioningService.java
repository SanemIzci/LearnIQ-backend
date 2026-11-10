package com.learniq.auth.service;

import com.learniq.auth.dto.*;
import com.learniq.common.tenant.TenantSchemaValidator;
import com.learniq.common.tenant.flyway.TenantSchemaMigrator;
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

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SUPER_ADMIN tenant lifecycle (Faz B / Karar 1-A: tam otomasyon).
 *
 * Provision pipeline: duplicatesız INSERT → CREATE SCHEMA → Flyway tenant
 * migrations (TenantSchemaMigrator, tek kaynak SQL) → default ADMIN user.
 * Herhangi bir adım başarısız olursa marka geçen şema temizlenir (DROP
 * CASCADE) ve tenants kaydı silinir — yarım provisioning artifact bırakmaz.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

    private static final List<String> SUBSCRIPTION_STATUSES = List.of("ACTIVE", "SUSPENDED", "EXPIRED");

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final DataSource dataSource;

    // ── Listeleme ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TenantListResponse listTenants(String search, String status, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> params = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            where.append(" AND (name ILIKE ? OR id ILIKE ?) ");
            String like = "%" + search.trim() + "%";
            params.addAll(List.of(like, like));
        }
        if (status != null && !status.isBlank()) {
            String s = status.toUpperCase();
            if (!SUBSCRIPTION_STATUSES.contains(s)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "status must be one of: ACTIVE, SUSPENDED, EXPIRED");
            }
            where.append(" AND subscription_status = ? ");
            params.add(s);
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.tenants t" + where, Long.class, params.toArray());
        List<TenantSummary> content = jdbcTemplate.query(
                "SELECT id, name, domain, subscription_status, created_at "
                        + "FROM public.tenants t" + where
                        + " ORDER BY t.created_at DESC, t.id LIMIT ? OFFSET ?",
                (rs, i) -> new TenantSummary(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("domain"),
                        rs.getString("subscription_status"),
                        toLocal(rs.getTimestamp("created_at"))),
                joinParams(params, size, page));

        return new TenantListResponse(total != null ? total : 0, page, size, content);
    }

    @Transactional(readOnly = true)
    public TenantDetail getTenantDetail(String tenantId) {
        TenantSummary summary = loadTenantSummary(tenantId);
        return new TenantDetail(summary.id(), summary.name(), summary.domain(),
                summary.subscriptionStatus(), summary.createdAt(), countTenantUsers(tenantId));
    }

    private long countTenantUsers(String tenantId) {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + tenantSchema(tenantId) + ".users", Long.class);
            return count != null ? count : 0;
        } catch (RuntimeException e) {
            // şema yoksa (provision FAILED etc.) kullanıcı yok sayılır
            return 0;
        }
    }

    private String tenantSchema(String tenantId) {
        return TenantSchemaValidator.requireValid(tenantId);
    }

    /** Şema adını 400'e çevirir (IllegalArgumentException doğrudan 500 olmasın). */
    private String validatedSchema(String tenantId) {
        try {
            return tenantSchema(tenantId);
        } catch (IllegalArgumentException invalidSchema) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid tenant id: " + tenantId);
        }
    }

    // ── Provisioning ─────────────────────────────────────────────────────────

    // Bilinçli olarak @Transactional YAPILMAZ:
    // Bu metot Flyway (TenantSchemaMigrator) çağırır. Flyway havuzdan bağımsız bir bağlantı açar.
    // Eğer provisionTenant dış bir Spring transaction'ı içinde koşarsa, CREATE SCHEMA işlemi bu
    // transaction içinde exclusive kilit alır. Flyway bağımsız bağlantısıyla bu şemaya erişmeye
    // çalıştığında kilit bekler; dış transaction ise Flyway'in bitmesini beklediği için self-deadlock
    // oluşur ve thread donar. Hata durumunda rollback mekanizması aşağıdaki catch bloğundaki
    // cleanupFailedProvisioning() ile açıkça işletilir.
    public CreateTenantResponse provisionTenant(CreateTenantRequest req) {
        if (req.id() == null || req.name() == null || req.name().isBlank()
                || req.adminUsername() == null || req.adminUsername().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "id, name and adminUsername are required");
        }
        String schema = validatedSchema(req.id());
        if (req.adminPassword() != null && req.adminPassword().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "adminPassword must be at least 8 characters");
        }

        Long idCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.tenants WHERE id = ?", Long.class, schema);
        if (idCount != null && idCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tenant id already exists");
        }
        if (req.domain() != null && !req.domain().isBlank()) {
            Long domainCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM public.tenants WHERE domain = ?", Long.class, req.domain());
            if (domainCount != null && domainCount > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Tenant domain already exists");
            }
        }

        boolean generatedPassword = req.adminPassword() == null || req.adminPassword().isBlank();
        String adminPassword = generatedPassword ? generatePassword() : req.adminPassword();
        UUID adminId = UUID.randomUUID();
        String adminHash = passwordEncoder.encode(adminPassword);

        try {
            jdbcTemplate.update(
                    "INSERT INTO public.tenants (id, name, domain, subscription_status) VALUES (?, ?, ?, 'ACTIVE')",
                    schema, req.name(), req.domain());

            // Şema + tenant migrations (schema yaratımı Flyway'e bırakmayız:
            // CREATE SCHEMA, cockroaches için validation üzerinden doğrulanmış adla koşulur)
            jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            TenantSchemaMigrator.migrateTenant(dataSource, schema);

            jdbcTemplate.update(
                    "INSERT INTO " + schema + ".users (id, username, password_hash, email, full_name, role) "
                            + "VALUES (?, ?, ?, ?, ?, 'ADMIN')",
                    adminId, req.adminUsername(), adminHash,
                    req.adminEmail() != null ? req.adminEmail() : "admin@" + schema + ".learniq.local",
                    req.adminFullName() != null ? req.adminFullName() : req.adminUsername());
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tenant id, domain or admin username already exists");
        } catch (RuntimeException e) {
            // yarım provisioning artifact'ı temizle
            log.error("Tenant provisioning FAILED for '{}'; rolling back artifacts", schema, e);
            cleanupFailedProvisioning(schema);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Tenant provisioning failed: " + e.getMessage());
        }

        log.info("Tenant '{}' provisioned with schema and default admin '{}'", schema, req.adminUsername());
        return new CreateTenantResponse(
                new TenantSummary(schema, req.name(), req.domain(), "ACTIVE", LocalDateTime.now()),
                req.adminUsername(),
                generatedPassword ? adminPassword : null);
    }

    private void cleanupFailedProvisioning(String schema) {
        try {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        } catch (RuntimeException dropEx) {
            log.error("Could not drop schema '{}' after failed provisioning", schema, dropEx);
        }
        jdbcTemplate.update("DELETE FROM public.tenants WHERE id = ?", schema);
    }

    // ── Subscription yönetimi ────────────────────────────────────────────────

    @Transactional
    public TenantSummary updateSubscription(String tenantId, SubscriptionUpdateRequest req) {
        if (req == null || req.status() == null
                || !SUBSCRIPTION_STATUSES.contains(req.status().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "status must be one of: ACTIVE, SUSPENDED, EXPIRED");
        }
        TenantSummary loaded = loadTenantSummary(tenantId); // 404'e düşer
        String status = req.status().toUpperCase();
        jdbcTemplate.update(
                "UPDATE public.tenants SET subscription_status = ? WHERE id = ?", status, tenantId);
        log.info("Tenant '{}' subscription set to '{}' by SUPER_ADMIN", tenantId, status);
        return new TenantSummary(loaded.id(), loaded.name(), loaded.domain(), status, loaded.createdAt());
    }

    private TenantSummary loadTenantSummary(String tenantId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id, name, domain, subscription_status, created_at FROM public.tenants WHERE id = ?",
                    (rs, i) -> new TenantSummary(
                            rs.getString("id"),
                            rs.getString("name"),
                            rs.getString("domain"),
                            rs.getString("subscription_status"),
                            toLocal(rs.getTimestamp("created_at"))),
                    tenantId);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found: " + tenantId);
        }
    }

    // ── yardımcılar ──────────────────────────────────────────────────────────

    private static String generatePassword() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(14);
        for (int i = 0; i < 14; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
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
