package com.learniq.exam.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/students/me")
@RequiredArgsConstructor
public class StudentProfileController {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @PutMapping("/leaderboard-consent")
    public ResponseEntity<Void> updateLeaderboardConsent(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam boolean consent) {
            
        if (!"STUDENT".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String sql = """
            UPDATE users 
            SET leaderboard_consent = :consent, 
                leaderboard_consent_at = CURRENT_TIMESTAMP
            WHERE id = :id
        """;
        
        int rows = jdbcTemplate.update(sql, Map.of(
            "consent", consent,
            "id", UUID.fromString(userId)
        ));

        if (rows == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok().build();
    }
}
