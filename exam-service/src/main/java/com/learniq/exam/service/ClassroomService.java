package com.learniq.exam.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JDBC-based classroom management.
 * Uses SET search_path for multi-tenant schema switching.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassroomService {

    private final JdbcTemplate jdbcTemplate;

    // ── Schema switcher ──────────────────────────────────────────────────────

    private void setSchema(String tenantId) {
        jdbcTemplate.execute("SET search_path TO " + tenantId);
    }

    // ── Classroom CRUD ───────────────────────────────────────────────────────

    public Map<String, Object> createClassroom(String tenantId, String name) {
        setSchema(tenantId);
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO classrooms (id, name) VALUES (?, ?)",
                id, name);
        log.info("Classroom '{}' created in tenant '{}'", name, tenantId);
        return Map.of("id", id.toString(), "name", name);
    }

    /** ADMIN: returns all classrooms. TEACHER: only their classrooms. */
    public List<Map<String, Object>> listClassrooms(String tenantId, UUID requesterId, String role) {
        setSchema(tenantId);
        if ("ADMIN".equals(role) || "SUPER_ADMIN".equals(role)) {
            return jdbcTemplate.queryForList(
                    "SELECT id, name, created_at FROM classrooms ORDER BY name");
        }
        // TEACHER — only classrooms they are assigned to
        return jdbcTemplate.queryForList(
                """
                SELECT c.id, c.name, c.created_at
                FROM classrooms c
                JOIN classroom_teachers ct ON ct.classroom_id = c.id
                WHERE ct.teacher_id = ?
                ORDER BY c.name
                """, requesterId);
    }

    public Map<String, Object> getClassroom(String tenantId, UUID classroomId) {
        setSchema(tenantId);
        var rows = jdbcTemplate.queryForList(
                "SELECT id, name, created_at FROM classrooms WHERE id = ?",
                classroomId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Classroom not found");
        }
        return rows.get(0);
    }

    // ── Teacher management ───────────────────────────────────────────────────

    public void addTeacher(String tenantId, UUID classroomId, UUID teacherId) {
        assertClassroomExists(tenantId, classroomId);
        assertUserExists(tenantId, teacherId, "TEACHER");
        setSchema(tenantId);
        jdbcTemplate.update(
                "INSERT INTO classroom_teachers (classroom_id, teacher_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                classroomId, teacherId);
        log.info("Teacher {} added to classroom {} in tenant {}", teacherId, classroomId, tenantId);
    }

    public void removeTeacher(String tenantId, UUID classroomId, UUID teacherId) {
        setSchema(tenantId);
        int rows = jdbcTemplate.update(
                "DELETE FROM classroom_teachers WHERE classroom_id = ? AND teacher_id = ?",
                classroomId, teacherId);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Teacher not found in this classroom");
        }
    }

    public List<Map<String, Object>> listTeachers(String tenantId, UUID classroomId) {
        assertClassroomExists(tenantId, classroomId);
        setSchema(tenantId);
        return jdbcTemplate.queryForList(
                """
                SELECT u.id, u.username, u.full_name, u.email
                FROM users u
                JOIN classroom_teachers ct ON ct.teacher_id = u.id
                WHERE ct.classroom_id = ?
                ORDER BY u.full_name
                """, classroomId);
    }

    // ── Student management ───────────────────────────────────────────────────

    public void addStudent(String tenantId, UUID classroomId, UUID studentId) {
        assertClassroomExists(tenantId, classroomId);
        assertUserExists(tenantId, studentId, "STUDENT");
        setSchema(tenantId);
        jdbcTemplate.update(
                "INSERT INTO classroom_students (classroom_id, student_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                classroomId, studentId);
        log.info("Student {} added to classroom {} in tenant {}", studentId, classroomId, tenantId);
    }

    public void removeStudent(String tenantId, UUID classroomId, UUID studentId) {
        setSchema(tenantId);
        int rows = jdbcTemplate.update(
                "DELETE FROM classroom_students WHERE classroom_id = ? AND student_id = ?",
                classroomId, studentId);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found in this classroom");
        }
    }

    public List<Map<String, Object>> listStudents(String tenantId, UUID classroomId) {
        assertClassroomExists(tenantId, classroomId);
        setSchema(tenantId);
        return jdbcTemplate.queryForList(
                """
                SELECT u.id, u.username, u.full_name, u.email
                FROM users u
                JOIN classroom_students cs ON cs.student_id = u.id
                WHERE cs.classroom_id = ?
                ORDER BY u.full_name
                """, classroomId);
    }

    // ── Guards ───────────────────────────────────────────────────────────────

    private void assertClassroomExists(String tenantId, UUID classroomId) {
        setSchema(tenantId);
        var rows = jdbcTemplate.queryForList(
                "SELECT 1 FROM classrooms WHERE id = ?", classroomId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Classroom not found");
        }
    }

    private void assertUserExists(String tenantId, UUID userId, String expectedRole) {
        setSchema(tenantId);
        var rows = jdbcTemplate.queryForList(
                "SELECT 1 FROM users WHERE id = ? AND role = ? AND active = TRUE",
                userId, expectedRole);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    expectedRole + " user not found or inactive");
        }
    }
}
