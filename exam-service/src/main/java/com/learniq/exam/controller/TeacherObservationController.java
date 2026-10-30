package com.learniq.exam.controller;

import com.learniq.exam.dto.CreateObservationRequest;
import com.learniq.exam.dto.ObservationResponse;
import com.learniq.exam.dto.UpdateObservationRequest;
import com.learniq.exam.service.TeacherObservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Öğretmen Gözlem Notu API'si.
 *
 * POST   /api/v1/teachers/me/classrooms/{classroomId}/observations
 * GET    /api/v1/teachers/me/classrooms/{classroomId}/observations
 * GET    /api/v1/teachers/me/observations
 * GET    /api/v1/teachers/me/observations/{id}
 * PUT    /api/v1/teachers/me/observations/{id}
 * DELETE /api/v1/teachers/me/observations/{id}
 */
@RestController
@RequestMapping("/api/v1/teachers/me")
@RequiredArgsConstructor
public class TeacherObservationController {

    private final TeacherObservationService observationService;

    // ── Create ───────────────────────────────────────────────────────────────

    @PostMapping("/classrooms/{classroomId}/observations")
    public ResponseEntity<ObservationResponse> create(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID classroomId,
            @Valid @RequestBody CreateObservationRequest request) {

        requireTeacherOrAdmin(role);
        UUID teacherId = parseUuid(userId);
        ObservationResponse response = observationService.create(tenantId, teacherId, role, classroomId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── List by classroom ────────────────────────────────────────────────────

    @GetMapping("/classrooms/{classroomId}/observations")
    public ResponseEntity<List<ObservationResponse>> listByClassroom(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID classroomId) {

        requireTeacherOrAdmin(role);
        UUID teacherId = parseUuid(userId);
        return ResponseEntity.ok(observationService.listByClassroom(tenantId, teacherId, role, classroomId));
    }

    // ── List all (my observations) ────────────────────────────────────────────

    @GetMapping("/observations")
    public ResponseEntity<List<ObservationResponse>> listAll(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role) {

        requireTeacherOrAdmin(role);
        UUID teacherId = parseUuid(userId);
        return ResponseEntity.ok(observationService.listAll(teacherId));
    }

    // ── Get by id ────────────────────────────────────────────────────────────

    @GetMapping("/observations/{id}")
    public ResponseEntity<ObservationResponse> getById(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID id) {

        requireTeacherOrAdmin(role);
        UUID teacherId = parseUuid(userId);
        return ResponseEntity.ok(observationService.getById(teacherId, id));
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @PutMapping("/observations/{id}")
    public ResponseEntity<ObservationResponse> update(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateObservationRequest request) {

        requireTeacherOrAdmin(role);
        UUID teacherId = parseUuid(userId);
        return ResponseEntity.ok(observationService.update(teacherId, id, request));
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @DeleteMapping("/observations/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID id) {

        requireTeacherOrAdmin(role);
        UUID teacherId = parseUuid(userId);
        observationService.delete(teacherId, id);
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void requireTeacherOrAdmin(String role) {
        if (!"TEACHER".equals(role) && !"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem için öğretmen veya yönetici rolü gereklidir");
        }
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz kullanıcı ID formatı");
        }
    }
}
