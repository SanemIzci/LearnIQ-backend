package com.learniq.exam.controller;

import com.learniq.exam.dto.ExamCreateRequest;
import com.learniq.exam.dto.ExamResponse;
import com.learniq.exam.dto.ExamUpdateRequest;
import com.learniq.exam.service.ExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

    // ── Endpoint #1: Create Exam ───────────────────────────────────────────────

    /**
     * POST /api/v1/exams
     * Creates a new exam for the current tenant.
     */
    @PostMapping
    public ResponseEntity<ExamResponse> createExam(@Valid @RequestBody ExamCreateRequest request) {
        ExamResponse response = examService.createExam(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── Endpoint #2: List All Exams ────────────────────────────────────────────

    /**
     * GET /api/v1/exams
     * Lists all exams for the current tenant, ordered by exam date descending.
     */
    @GetMapping
    public ResponseEntity<List<ExamResponse>> listExams() {
        return ResponseEntity.ok(examService.listExams());
    }

    // ── Endpoint #3: Get Single Exam ───────────────────────────────────────────

    /**
     * GET /api/v1/exams/{examId}
     * Returns a single exam with its mapped subjects.
     */
    @GetMapping("/{examId}")
    public ResponseEntity<ExamResponse> getExam(@PathVariable UUID examId) {
        return ResponseEntity.ok(examService.getExamById(examId));
    }

    // ── Endpoint #4: Update Exam ───────────────────────────────────────────────

    /**
     * PUT /api/v1/exams/{examId}
     * Updates an existing exam using PATCH semantics (only non-null fields applied).
     */
    @PutMapping("/{examId}")
    public ResponseEntity<ExamResponse> updateExam(@PathVariable UUID examId,
                                                   @Valid @RequestBody ExamUpdateRequest request) {
        return ResponseEntity.ok(examService.updateExam(examId, request));
    }

    // ── Endpoint #5: Delete Exam ───────────────────────────────────────────────

    /**
     * DELETE /api/v1/exams/{examId}
     * Deletes an exam. Cascades to exam_subjects, student_exam_results,
     * and student_subject_results per the ON DELETE CASCADE constraints in SQL.
     */
    @DeleteMapping("/{examId}")
    public ResponseEntity<Void> deleteExam(@PathVariable UUID examId) {
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
                                                   @PathVariable UUID subjectId) {
        return ResponseEntity.ok(examService.addSubjectToExam(examId, subjectId));
    }

    // ── Endpoint #9: Remove Subject from Exam ─────────────────────────────────

    /**
     * DELETE /api/v1/exams/{examId}/subjects/{subjectId}
     * Removes a subject mapping from an exam.
     */
    @DeleteMapping("/{examId}/subjects/{subjectId}")
    public ResponseEntity<ExamResponse> removeSubject(@PathVariable UUID examId,
                                                      @PathVariable UUID subjectId) {
        return ResponseEntity.ok(examService.removeSubjectFromExam(examId, subjectId));
    }
}
