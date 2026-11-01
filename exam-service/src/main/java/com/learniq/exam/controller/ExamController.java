package com.learniq.exam.controller;

import com.learniq.exam.dto.ExamCreateRequest;
import com.learniq.exam.dto.ExamResponse;
import com.learniq.exam.dto.ExamUpdateRequest;
import com.learniq.exam.security.AccessControlService;
import com.learniq.exam.service.ExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Exam management API.
 *
 * RBAC rules (fail-closed, enforced here):
 *   ADMIN / SUPER_ADMIN → full access
 *   TEACHER             → own-exam operations (validated via exam_classrooms ∩ classroom_teachers)
 *   STUDENT             → no access to exam management (students use /students/me)
 */
@RestController
@RequestMapping("/api/v1/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;
    private final com.learniq.exam.service.ExamFinalizationService examFinalizationService;
    private final AccessControlService accessControlService;

    // ── Guards ───────────────────────────────────────────────────────────────

    private void requireStaffRole(String role) {
        if (role == null || role.isBlank()
                || !"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role) && !"TEACHER".equals(role)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "Access denied");
        }
    }

    private void validateExamModify(String tenantId, String role, String userId, UUID examId) {
        requireStaffRole(role);
        accessControlService.validateExamAccess(tenantId, UUID.fromString(userId), role, examId);
    }

    // ── Endpoint #1: Create Exam ───────────────────────────────────────────────

    /**
     * POST /api/v1/exams
     * Creates a new exam for the current tenant.
     */
    @PostMapping
    public ResponseEntity<ExamResponse> createExam(@Valid @RequestBody ExamCreateRequest request,
                                                   @RequestHeader("X-User-Role") String role) {
        requireStaffRole(role);
        ExamResponse response = examService.createExam(request);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(response);
    }

    // ── Endpoint #2: List All Exams ────────────────────────────────────────────

    /**
     * GET /api/v1/exams
     * Lists all exams for the current tenant, ordered by exam date descending.
     */
    @GetMapping
    public ResponseEntity<List<ExamResponse>> listExams(@RequestHeader("X-User-Role") String role) {
        requireStaffRole(role);
        return ResponseEntity.ok(examService.listExams());
    }

    // ── Endpoint #3: Get Single Exam ───────────────────────────────────────────

    /**
     * GET /api/v1/exams/{examId}
     * Returns a single exam with its mapped subjects.
     */
    @GetMapping("/{examId}")
    public ResponseEntity<ExamResponse> getExam(@PathVariable UUID examId,
                                                @RequestHeader("X-User-Role") String role) {
        requireStaffRole(role);
        return ResponseEntity.ok(examService.getExamById(examId));
    }

    // ── Endpoint #4: Update Exam ───────────────────────────────────────────────

    /**
     * PUT /api/v1/exams/{examId}
     * Updates an existing exam using PATCH semantics (only non-null fields applied).
     */
    @PutMapping("/{examId}")
    public ResponseEntity<ExamResponse> updateExam(@PathVariable UUID examId,
                                                   @Valid @RequestBody ExamUpdateRequest request,
                                                   @RequestHeader("X-Tenant-Id") String tenantId,
                                                   @RequestHeader("X-User-Id") String userId,
                                                   @RequestHeader("X-User-Role") String role) {
        validateExamModify(tenantId, role, userId, examId);
        return ResponseEntity.ok(examService.updateExam(examId, request));
    }

    // ── Endpoint #5: Delete Exam ───────────────────────────────────────────────

    /**
     * DELETE /api/v1/exams/{examId}
     * Deletes an exam. Cascades to exam_subjects, student_exam_results,
     * and student_subject_results per the ON DELETE CASCADE constraints in SQL.
     */
    @DeleteMapping("/{examId}")
    public ResponseEntity<Void> deleteExam(@PathVariable UUID examId,
                                           @RequestHeader("X-Tenant-Id") String tenantId,
                                           @RequestHeader("X-User-Id") String userId,
                                           @RequestHeader("X-User-Role") String role) {
        validateExamModify(tenantId, role, userId, examId);
        examService.deleteExam(examId);
        return ResponseEntity.noContent().build();
    }

    // ── Endpoint #8: Add Subject to Exam ──────────────────────────────────────

    /**
     * POST /api/v1/exams/{examId}/subjects/{subjectId}
     * Adds an existing subject to an exam (populates exam_subjects bridge table).
     */
    @PostMapping("/{examId}/subjects/{subjectId}")
    public ResponseEntity<ExamResponse> addSubject(@PathVariable UUID examId,
                                                   @PathVariable UUID subjectId,
                                                   @RequestHeader("X-Tenant-Id") String tenantId,
                                                   @RequestHeader("X-User-Id") String userId,
                                                   @RequestHeader("X-User-Role") String role) {
        validateExamModify(tenantId, role, userId, examId);
        return ResponseEntity.ok(examService.addSubjectToExam(examId, subjectId));
    }

    // ── Endpoint #9: Remove Subject from Exam ─────────────────────────────────

    /**
     * DELETE /api/v1/exams/{examId}/subjects/{subjectId}
     * Removes a subject mapping from an exam.
     */
    @DeleteMapping("/{examId}/subjects/{subjectId}")
    public ResponseEntity<ExamResponse> removeSubject(@PathVariable UUID examId,
                                                      @PathVariable UUID subjectId,
                                                      @RequestHeader("X-Tenant-Id") String tenantId,
                                                      @RequestHeader("X-User-Id") String userId,
                                                      @RequestHeader("X-User-Role") String role) {
        validateExamModify(tenantId, role, userId, examId);
        return ResponseEntity.ok(examService.removeSubjectFromExam(examId, subjectId));
    }

    // ── Endpoint #10: Finalize Exam ───────────────────────────────────────────

    /**
     * POST /api/v1/exams/{examId}/finalize
     * Closes the exam, calculates rankings/percentiles, and triggers AI reports.
     */
    @PostMapping("/{examId}/finalize")
    public ResponseEntity<Void> finalizeExam(@PathVariable UUID examId,
                                             @RequestHeader("X-Tenant-Id") String tenantId,
                                             @RequestHeader("X-User-Id") String userId,
                                             @RequestHeader("X-User-Role") String role) {
        validateExamModify(tenantId, role, userId, examId);
        examFinalizationService.finalizeExam(examId);
        return ResponseEntity.ok().build();
    }
}
