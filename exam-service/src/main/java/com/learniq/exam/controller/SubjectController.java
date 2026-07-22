package com.learniq.exam.controller;

import com.learniq.exam.dto.SubjectRequest;
import com.learniq.exam.dto.SubjectResponse;
import com.learniq.exam.service.SubjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;

    /**
     * POST /api/v1/subjects
     * Creates a new subject within the current tenant's schema.
     * The tenant is resolved automatically via the TenantContext (set by TenantInterceptor).
     */
    @PostMapping
    public ResponseEntity<SubjectResponse> createSubject(@Valid @RequestBody SubjectRequest request) {
        SubjectResponse response = subjectService.createSubject(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/v1/subjects
     * Returns all subjects for the current tenant, sorted alphabetically.
     */
    @GetMapping
    public ResponseEntity<List<SubjectResponse>> listSubjects() {
        return ResponseEntity.ok(subjectService.listSubjects());
    }

    /**
     * GET /api/v1/subjects/{subjectId}
     * Returns a single subject by ID.
     */
    @GetMapping("/{subjectId}")
    public ResponseEntity<SubjectResponse> getSubject(@PathVariable UUID subjectId) {
        return ResponseEntity.ok(subjectService.getSubjectById(subjectId));
    }
}
