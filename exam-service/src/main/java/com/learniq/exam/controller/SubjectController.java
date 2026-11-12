package com.learniq.exam.controller;

import com.learniq.exam.dto.SubjectRequest;
import com.learniq.exam.dto.SubjectResponse;
import com.learniq.exam.service.SubjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    public ResponseEntity<SubjectResponse> createSubject(@Valid @RequestBody SubjectRequest request,
                                                         @RequestHeader(value = "X-User-Role", required = false) String role) {
        // fail-closed: rol bilgisi yoksa/boşsa ders oluşturma reddedilir
        if (role == null || role.isBlank()
                || !"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role) && !"TEACHER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only teachers and administrators can create subjects.");
        }
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

    /**
     * PUT /api/v1/subjects/{subjectId}
     * Updates an existing subject's name within the current tenant.
     */
    @PutMapping("/{subjectId}")
    public ResponseEntity<SubjectResponse> updateSubject(@PathVariable UUID subjectId,
                                                         @Valid @RequestBody SubjectRequest request,
                                                         @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (role == null || role.isBlank()
                || !"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role) && !"TEACHER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only teachers and administrators can update subjects.");
        }
        SubjectResponse response = subjectService.updateSubject(subjectId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/v1/subjects/{subjectId}
     * Deletes a subject within the current tenant.
     */
    @DeleteMapping("/{subjectId}")
    public ResponseEntity<Void> deleteSubject(@PathVariable UUID subjectId,
                                              @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (role == null || role.isBlank()
                || !"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role) && !"TEACHER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only teachers and administrators can delete subjects.");
        }
        subjectService.deleteSubject(subjectId);
        return ResponseEntity.noContent().build();
    }
}
