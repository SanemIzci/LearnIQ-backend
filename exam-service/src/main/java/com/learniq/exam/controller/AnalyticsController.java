package com.learniq.exam.controller;

import com.learniq.common.dto.StudentTrendReportDto;
import com.learniq.exam.security.AccessControlService;
import com.learniq.exam.service.AnalyticsEventPublisher;
import com.learniq.exam.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final AccessControlService accessControlService;

    /**
     * GET /api/v1/analytics/students/{studentId}/history
     *
     * ADMIN  → tüm öğrenciler
     * TEACHER → kendi sınıfındaki öğrenciler
     * STUDENT → sadece kendisi
     */
    @GetMapping("/students/{studentId}/history")
    public ResponseEntity<StudentTrendReportDto> getStudentHistory(
            @PathVariable UUID studentId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-User-Id",    required = false) String userId,
            @RequestHeader(value = "X-User-Role",  required = false) String role) {

        // fail-closed: tenant ve rol header'ı zorunlu; default'lar kaldırıldı
        if (tenantId == null || tenantId.isBlank() || role == null || role.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Missing tenant or role");
        }
        UUID requesterId = resolveRequesterId(userId, role, studentId);
        accessControlService.validateStudentAccess(tenantId, requesterId, role, studentId);

        StudentTrendReportDto report = analyticsService.getStudentTrendReport(studentId);
        return ResponseEntity.ok(report);
    }

    /**
     * POST /api/v1/analytics/students/{studentId}/reports/generate
     *
     * ADMIN  → herhangi bir öğrenci için
     * TEACHER → kendi sınıfındaki öğrenciler için
     * STUDENT → sadece kendisi için
     */
    @PostMapping("/students/{studentId}/reports/generate")
    public ResponseEntity<Void> generateAiReport(
            @PathVariable UUID studentId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-User-Id",    required = false) String userId,
            @RequestHeader(value = "X-User-Role",  required = false) String role) {

        // fail-closed: tenant ve rol header'ı zorunlu; default'lar kaldırıldı
        if (tenantId == null || tenantId.isBlank() || role == null || role.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Missing tenant or role");
        }
        UUID requesterId = resolveRequesterId(userId, role, studentId);
        accessControlService.validateStudentAccess(tenantId, requesterId, role, studentId);

        StudentTrendReportDto report = analyticsService.getStudentTrendReport(studentId);
        analyticsEventPublisher.publishAiReportRequest(studentId, report);
        return ResponseEntity.accepted().build();
    }

    /**
     * requesterId yalnızca STUDENT self-check fallback'inde studentId'ye düşebilir;
     * TEACHER/ADMIN için kullanıcı kimliği zorunludur (aksi halde 403).
     */
    private UUID resolveRequesterId(String userId, String role, UUID studentId) {
        if (userId != null && !userId.isBlank()) {
            return UUID.fromString(userId);
        }
        if ("STUDENT".equals(role)) {
            return studentId;
        }
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Missing user id");
    }
}
