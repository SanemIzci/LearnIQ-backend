package com.learniq.ai.controller;

import com.learniq.ai.chat.AdminChatService;
import com.learniq.ai.dto.AdminChatRequest;
import com.learniq.ai.dto.AdminChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Admin RAG chat endpoint'i. Fail-closed: rol header'ı gezgin/gateway tarafından
 * JWT'den yazılır; eksik/boş/neadmin roller erişemez (SubjectTopicController
 * deseninin aynısı; here the allowed role is ADMIN only, super admin allowed).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ai")
@RequiredArgsConstructor
public class AdminChatController {

    private final AdminChatService adminChatService;

    @PostMapping("/chat")
    public ResponseEntity<AdminChatResponse> chat(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestBody AdminChatRequest request) {

        checkPermission(role);
        validateRequest(tenantId, request);

        log.info("Admin chat istendi: tenantId={}", tenantId);
        AdminChatResponse response = adminChatService.chat(tenantId, request);
        return ResponseEntity.ok(response);
    }

    private void checkPermission(String role) {
        if (role == null || role.isBlank()
                || !"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only administrators can use the AI assistant.");
        }
    }

    private void validateRequest(String tenantId, AdminChatRequest request) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing tenant id");
        }
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is required");
        }
        if (request.message().length() > 4000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Message exceeds the maximum length of 4000 characters");
        }
    }
}
