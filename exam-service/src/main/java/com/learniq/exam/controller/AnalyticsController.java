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
            @RequestHeader(value = "X-Tenant-Id",  required = false, defaultValue = "public") String tenantId,
            @RequestHeader(value = "X-User-Id",    required = false) String userId,
            @RequestHeader(value = "X-User-Role",  required = false, defaultValue = "STUDENT") String role) {

        UUID requesterId = userId != null ? UUID.fromString(userId) : studentId;
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
            @RequestHeader(value = "X-Tenant-Id",  required = false, defaultValue = "public") String tenantId,
            @RequestHeader(value = "X-User-Id",    required = false) String userId,
            @RequestHeader(value = "X-User-Role",  required = false, defaultValue = "STUDENT") String role) {

        UUID requesterId = userId != null ? UUID.fromString(userId) : studentId;
        accessControlService.validateStudentAccess(tenantId, requesterId, role, studentId);

        StudentTrendReportDto report = analyticsService.getStudentTrendReport(studentId);
        analyticsEventPublisher.publishAiReportRequest(studentId, report);
        return ResponseEntity.accepted().build();
    }
}
