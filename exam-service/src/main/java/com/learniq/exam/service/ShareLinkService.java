package com.learniq.exam.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.common.tenant.TenantContext;
import com.learniq.common.tenant.TenantSchemaValidator;
import com.learniq.exam.dto.StudentDashboardResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareLinkService {

    private final StudentDashboardService studentDashboardService;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private static final SecureRandom secureRandom = new SecureRandom();

    public String generateShareLink(UUID examId, UUID studentId) {
        // 1. Snapshot alınacak veriyi hazırla (Öğrenci Dashboard'u)
        StudentDashboardResponse dashboard = studentDashboardService.getDashboard(examId, studentId);

        // 2. Güvenli Token üret (32 byte -> Base64Url encoded = 43 chars)
        //    Token'a tenant şeması öneklenir: public endpoint'te header olmadığından
        //    satırın hangi tenant şemasında aranacağı token üzerinden çözülür.
        String schema = TenantSchemaValidator.requireValid(TenantContext.getCurrentTenant());
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = schema + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        // 3. JSONB formatına çevir
        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(dashboard);
        } catch (JsonProcessingException e) {
            log.error("Snapshot serialize edilemedi", e);
            throw new RuntimeException("Paylaşım linki oluşturulurken hata meydana geldi.");
        }

        // 4. Veritabanına kaydet (Geçerlilik süresi: 30 gün)
        String sql = """
            INSERT INTO %s.shared_links (token, student_id, snapshot, created_at, expires_at)
            VALUES (:token, :studentId, CAST(:snapshot AS JSONB), :createdAt, :expiresAt)
            """.formatted(schema);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("token", token)
                .addValue("studentId", studentId)
                .addValue("snapshot", snapshotJson, Types.OTHER) // Types.OTHER is needed for Postgres JSONB in JdbcTemplate
                .addValue("createdAt", LocalDateTime.now())
                .addValue("expiresAt", LocalDateTime.now().plusDays(30));

        jdbcTemplate.update(sql, params);

        return token;
    }

    public StudentDashboardResponse getSharedSnapshot(String token) {
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            throw new IllegalArgumentException("Geçersiz paylaşım linki.");
        }
        String schema = TenantSchemaValidator.requireValid(token.substring(0, dot));

        // Var olmayan kiracı şeması → BadSqlGrammar/500 yerine 400 döner.
        List<Map<String, Object>> schemaExists = jdbcTemplate.queryForList(
                "SELECT 1 FROM information_schema.schemata WHERE schema_name = :schema",
                new MapSqlParameterSource("schema", schema));
        if (schemaExists.isEmpty()) {
            throw new IllegalArgumentException("Geçersiz veya süresi dolmuş paylaşım linki.");
        }

        String sql = """
            SELECT snapshot, expires_at, revoked_at 
            FROM %s.shared_links 
            WHERE token = :token
            """.formatted(schema);

        return jdbcTemplate.query(sql, new MapSqlParameterSource("token", token), rs -> {
            if (!rs.next()) {
                throw new IllegalArgumentException("Geçersiz veya süresi dolmuş paylaşım linki.");
            }

            LocalDateTime expiresAt = rs.getTimestamp("expires_at").toLocalDateTime();
            if (expiresAt.isBefore(LocalDateTime.now())) {
                throw new IllegalArgumentException("Bu paylaşım linkinin süresi dolmuş.");
            }

            if (rs.getTimestamp("revoked_at") != null) {
                throw new IllegalArgumentException("Bu paylaşım linki iptal edilmiş.");
            }

            String json = rs.getString("snapshot");
            try {
                return objectMapper.readValue(json, StudentDashboardResponse.class);
            } catch (JsonProcessingException e) {
                log.error("Snapshot parse edilemedi", e);
                throw new RuntimeException("Snapshot verisi okunamadı.");
            }
        });
    }
}
