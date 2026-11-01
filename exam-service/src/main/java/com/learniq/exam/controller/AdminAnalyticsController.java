package com.learniq.exam.controller;

import com.learniq.exam.dto.AdminDashboardResponse;
import com.learniq.exam.dto.ClassSubjectTrendRow;
import com.learniq.exam.dto.RecentObservationDto;
import com.learniq.exam.dto.SubjectScorecardResponse;
import com.learniq.exam.service.AdminAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Admin analitiği (Faz D). Tenant-ölçekli, hesaplamasız — class_subject_trends fact tablosundan okur.
 * Fail-closed: ADMIN | SUPER_ADMIN dışındaki roller (rol header'ı eksik dahil) 403.
 */
@RestController
@RequestMapping("/api/v1/analytics/admin")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;

    private void requirePlatformAdmin(String role) {
        if (!"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    private int clamp(int requested, int max, int fallback) {
        if (requested <= 0) {
            return fallback;
        }
        return Math.min(requested, max);
    }

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardResponse> getDashboard(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Role") String role) {

        requirePlatformAdmin(role);
        return ResponseEntity.ok(adminAnalyticsService.getDashboard(tenantId));
    }

    @GetMapping("/subjects")
    public ResponseEntity<SubjectScorecardResponse> getSubjectScorecard(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(name = "examId", required = false) UUID examId) {

        requirePlatformAdmin(role);
        return ResponseEntity.ok(adminAnalyticsService.getSubjectScorecard(tenantId, examId));
    }

    @GetMapping("/trends")
    public ResponseEntity<List<ClassSubjectTrendRow>> getTrends(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(name = "subjectId", required = false) UUID subjectId,
            @RequestParam(name = "limit", required = false, defaultValue = "50") int limit) {

        requirePlatformAdmin(role);
        return ResponseEntity.ok(adminAnalyticsService.getTrends(tenantId, subjectId, clamp(limit, 200, 50)));
    }

    @GetMapping("/observations/recent")
    public ResponseEntity<List<RecentObservationDto>> getRecentObservations(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(name = "limit", required = false, defaultValue = "10") int limit) {

        requirePlatformAdmin(role);
        return ResponseEntity.ok(adminAnalyticsService.getRecentObservations(tenantId, clamp(limit, 50, 10)));
    }
}
