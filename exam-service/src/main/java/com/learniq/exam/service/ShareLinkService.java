package com.learniq.exam.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

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
            INSERT INTO shared_links (token, student_id, snapshot, created_at, expires_at)
            VALUES (:token, :studentId, CAST(:snapshot AS JSONB), :createdAt, :expiresAt)
        """;

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
        String sql = """
            SELECT snapshot, expires_at, revoked_at 
            FROM shared_links 
            WHERE token = :token
        """;

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
