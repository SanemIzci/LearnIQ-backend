package com.learniq.exam.controller;

import com.learniq.exam.dto.ExamTypeSubjectRuleRequest;
import com.learniq.exam.dto.ExamTypeSubjectRuleResponse;
import com.learniq.exam.service.ExamRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exam-rules")
@RequiredArgsConstructor
public class ExamRuleController {

    private final ExamRuleService examRuleService;

    /**
     * GET /api/v1/exam-rules/{examType}
     * Lists (examType, subjectId) net-calculation rules for the current tenant.
     */
    @GetMapping("/{examType}")
    public ResponseEntity<List<ExamTypeSubjectRuleResponse>> listRules(
            @PathVariable String examType,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireStaffRole(role);
        return ResponseEntity.ok(examRuleService.listRules(examType));
    }

    /**
     * PUT /api/v1/exam-rules/{examType}/{subjectId}
     * Creates or updates the net-calculation rule. Required before students can submit results.
     */
    @PutMapping("/{examType}/{subjectId}")
    public ResponseEntity<ExamTypeSubjectRuleResponse> upsertRule(
            @PathVariable String examType,
            @PathVariable UUID subjectId,
            @Valid @RequestBody ExamTypeSubjectRuleRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireStaffRole(role);
        return ResponseEntity.status(HttpStatus.OK)
                .body(examRuleService.upsertRule(examType, subjectId, request));
    }

    private void requireStaffRole(String role) {
        if (role == null || role.isBlank()
                || !"ADMIN".equals(role) && !"TEACHER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only teachers and tenant administrators can manage exam rules.");
        }
    }
}
