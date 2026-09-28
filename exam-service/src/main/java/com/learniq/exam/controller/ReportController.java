package com.learniq.exam.controller;

import com.learniq.exam.security.AccessControlService;
import com.learniq.exam.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Report fetching API.
 *
 * RBAC (enforced via AccessControlService):
 *   SUPER_ADMIN → all tenants
 *   ADMIN       → any student in their tenant
 *   TEACHER     → students in their classrooms
 *   STUDENT     → only their own reports
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final AccessControlService accessControlService;

    /**
     * GET /api/v1/reports/students/{studentId}
     * List all report files for a student (metadata only, no content).
     */
    @GetMapping("/students/{studentId}")
    public ResponseEntity<List<Map<String, Object>>> listReports(
            @PathVariable UUID studentId,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Id")   String userId,
            @RequestHeader("X-User-Role") String role) {

        accessControlService.validateStudentAccess(tenantId,
                UUID.fromString(userId), role, studentId);

        List<Map<String, Object>> reports = reportService.listReports(tenantId, studentId.toString());
        return ResponseEntity.ok(reports);
    }

    /**
     * GET /api/v1/reports/students/{studentId}/{filename}
     *
     * filename = "latest"         → en son raporu döner
     * filename = "student_xxx.md" → belirtilen raporu döner
     *
     * (İki ayrı endpoint yerine tek endpoint kullanıyoruz çünkü Spring MVC'de
     *  /latest ve /{filename} aynı URL pattern'ine sahip olduğunda routing
     *  belirsizliği yaşanıyor.)
     */
    @GetMapping(value = "/students/{studentId}/{filename}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getReport(
            @PathVariable UUID studentId,
            @PathVariable String filename,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Id")   String userId,
            @RequestHeader("X-User-Role") String role) {

        accessControlService.validateStudentAccess(tenantId,
                UUID.fromString(userId), role, studentId);

        String content;
        String cleanFilename = filename.trim();
        if ("latest".equals(cleanFilename)) {
            content = reportService.getLatestReport(tenantId, studentId.toString());
        } else {
            content = reportService.getReport(tenantId, studentId.toString(), cleanFilename);
        }
        return ResponseEntity.ok(content);
    }
}
