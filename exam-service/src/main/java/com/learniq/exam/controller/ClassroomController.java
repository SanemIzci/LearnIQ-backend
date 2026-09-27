package com.learniq.exam.controller;

import com.learniq.exam.dto.AddMemberRequest;
import com.learniq.exam.dto.CreateClassroomRequest;
import com.learniq.exam.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Classroom management API.
 *
 * RBAC rules (enforced here, not in gateway):
 *   ADMIN      → full access
 *   TEACHER    → read-only (list/get their own classrooms + students)
 *   STUDENT    → no access
 *   SUPER_ADMIN → full access
 */
@RestController
@RequestMapping("/api/v1/classrooms")
@RequiredArgsConstructor
public class ClassroomController {

    private final ClassroomService classroomService;

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void requireAdmin(String role) {
        if (!"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only ADMIN can perform this action");
        }
    }

    private void requireAdminOrTeacher(String role) {
        if (!"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role) && !"TEACHER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied");
        }
    }

    // ── Classroom CRUD ───────────────────────────────────────────────────────

    /**
     * POST /api/v1/classrooms
     * Create a new classroom. ADMIN only.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createClassroom(
            @RequestBody CreateClassroomRequest req,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Role")  String role) {

        requireAdmin(role);

        if (req.getName() == null || req.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Classroom name is required");
        }

        Map<String, Object> created = classroomService.createClassroom(tenantId, req.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * GET /api/v1/classrooms
     * ADMIN → all classrooms
     * TEACHER → only their assigned classrooms
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listClassrooms(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Role")  String role,
            @RequestHeader("X-User-Id")    String userId) {

        requireAdminOrTeacher(role);
        List<Map<String, Object>> classrooms = classroomService.listClassrooms(
                tenantId, UUID.fromString(userId), role);
        return ResponseEntity.ok(classrooms);
    }

    /**
     * GET /api/v1/classrooms/{id}
     * Get classroom detail. ADMIN or assigned TEACHER.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getClassroom(
            @PathVariable UUID id,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Role")  String role) {

        requireAdminOrTeacher(role);
        return ResponseEntity.ok(classroomService.getClassroom(tenantId, id));
    }

    // ── Teacher management ───────────────────────────────────────────────────

    /**
     * POST /api/v1/classrooms/{id}/teachers
     * Add a teacher to classroom. ADMIN only.
     * Body: { "userId": "teacher-uuid" }
     */
    @PostMapping("/{id}/teachers")
    public ResponseEntity<Map<String, String>> addTeacher(
            @PathVariable UUID id,
            @RequestBody AddMemberRequest req,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Role")  String role) {

        requireAdmin(role);
        classroomService.addTeacher(tenantId, id, req.getUserId());
        return ResponseEntity.ok(Map.of("message", "Teacher added successfully"));
    }

    /**
     * DELETE /api/v1/classrooms/{id}/teachers/{teacherId}
     * Remove a teacher from classroom. ADMIN only.
     */
    @DeleteMapping("/{id}/teachers/{teacherId}")
    public ResponseEntity<Map<String, String>> removeTeacher(
            @PathVariable UUID id,
            @PathVariable UUID teacherId,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Role")  String role) {

        requireAdmin(role);
        classroomService.removeTeacher(tenantId, id, teacherId);
        return ResponseEntity.ok(Map.of("message", "Teacher removed successfully"));
    }

    /**
     * GET /api/v1/classrooms/{id}/teachers
     * List teachers in a classroom. ADMIN or assigned TEACHER.
     */
    @GetMapping("/{id}/teachers")
    public ResponseEntity<List<Map<String, Object>>> listTeachers(
            @PathVariable UUID id,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Role")  String role) {

        requireAdminOrTeacher(role);
        return ResponseEntity.ok(classroomService.listTeachers(tenantId, id));
    }

    // ── Student management ───────────────────────────────────────────────────

    /**
     * POST /api/v1/classrooms/{id}/students
     * Add a student to classroom. ADMIN only.
     * Body: { "userId": "student-uuid" }
     */
    @PostMapping("/{id}/students")
    public ResponseEntity<Map<String, String>> addStudent(
            @PathVariable UUID id,
            @RequestBody AddMemberRequest req,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Role")  String role) {

        requireAdmin(role);
        classroomService.addStudent(tenantId, id, req.getUserId());
        return ResponseEntity.ok(Map.of("message", "Student added successfully"));
    }

    /**
     * DELETE /api/v1/classrooms/{id}/students/{studentId}
     * Remove a student from classroom. ADMIN only.
     */
    @DeleteMapping("/{id}/students/{studentId}")
    public ResponseEntity<Map<String, String>> removeStudent(
            @PathVariable UUID id,
            @PathVariable UUID studentId,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Role")  String role) {

        requireAdmin(role);
        classroomService.removeStudent(tenantId, id, studentId);
        return ResponseEntity.ok(Map.of("message", "Student removed successfully"));
    }

    /**
     * GET /api/v1/classrooms/{id}/students
     * List students in a classroom. ADMIN or assigned TEACHER.
     */
    @GetMapping("/{id}/students")
    public ResponseEntity<List<Map<String, Object>>> listStudents(
            @PathVariable UUID id,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Role")  String role) {

        requireAdminOrTeacher(role);
        return ResponseEntity.ok(classroomService.listStudents(tenantId, id));
    }
}
