package com.learniq.ai.controller;

import com.learniq.ai.rag.RagBackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * RAG operasyon endpoint'leri (admin-only, fail-closed rol kontrolü).
 *
 * Model geçişi backfill'i buradan tetiklenir. DLT replay endpoint'i, güvenlik /
 * idempotency / offset yönetimi netleşene kadar bilinçli olarak eklenmemiştir.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ai/rag")
@RequiredArgsConstructor
public class RagAdminController {

    private final RagBackfillService backfillService;

    @PostMapping("/backfill")
    public ResponseEntity<Map<String, Object>> backfill(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(value = "batchSize", defaultValue = "50") int batchSize) {

        checkPermission(role);
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing tenant id");
        }

        RagBackfillService.BackfillResult result = backfillService.backfill(tenantId, batchSize);
        log.info("RAG backfill istendi: tenant={} batchSize={} → {}", tenantId, batchSize, result);
        return ResponseEntity.ok(Map.of(
                "candidates", result.candidates(),
                "updated", result.updated(),
                "failed", result.failed()));
    }

    private void checkPermission(String role) {
        if (role == null || role.isBlank()
                || !"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only administrators can trigger RAG backfill.");
        }
    }
}
