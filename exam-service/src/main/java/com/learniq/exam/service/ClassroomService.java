package com.learniq.exam.service;

import com.learniq.common.tenant.TenantContext;
import com.learniq.common.tenant.TenantSchemaValidator;
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
 * All statements are schema-qualified via TenantContext (validated by TenantSchemaValidator).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassroomService {

    private final JdbcTemplate jdbcTemplate;

    // ── Schema resolution ────────────────────────────────────────────────────

    private String schema() {
        return TenantSchemaValidator.requireValid(TenantContext.getCurrentTenant());
    }

    // ── Classroom CRUD ───────────────────────────────────────────────────────

    public Map<String, Object> createClassroom(String tenantId, String name) {
        String schema = schema();
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO %s.classrooms (id, name) VALUES (?, ?)".formatted(schema),
                id, name);
        log.info("Classroom '{}' created in tenant '{}'", name, tenantId);
        return Map.of("id", id.toString(), "name", name);
    }

    /** ADMIN: returns all classrooms. TEACHER: only their classrooms. */
    public List<Map<String, Object>> listClassrooms(String tenantId, UUID requesterId, String role) {
        String schema = schema();
        if ("ADMIN".equals(role) || "SUPER_ADMIN".equals(role)) {
            return jdbcTemplate.queryForList(
                    "SELECT id, name, created_at FROM %s.classrooms ORDER BY name".formatted(schema));
        }
        // TEACHER — only classrooms they are assigned to
        return jdbcTemplate.queryForList(
                """
                SELECT c.id, c.name, c.created_at
                FROM %1$s.classrooms c
                JOIN %1$s.classroom_teachers ct ON ct.classroom_id = c.id
                WHERE ct.teacher_id = ?
                ORDER BY c.name
                """.formatted(schema), requesterId);
    }

    public com.learniq.common.api.PageResponse<Map<String, Object>> listClassrooms(
            String tenantId, UUID requesterId, String role, int page, int size) {
        String schema = schema();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = safePage * safeSize;

        String countSql;
        String dataSql;
        Object[] countArgs;
        Object[] dataArgs;

        if ("ADMIN".equals(role) || "SUPER_ADMIN".equals(role)) {
            countSql = "SELECT COUNT(*) FROM %s.classrooms".formatted(schema);
            countArgs = new Object[]{};
            dataSql = "SELECT id, name, created_at FROM %s.classrooms ORDER BY name LIMIT ? OFFSET ?".formatted(schema);
            dataArgs = new Object[]{safeSize, offset};
        } else {
            countSql = """
                    SELECT COUNT(*)
                    FROM %1$s.classrooms c
                    JOIN %1$s.classroom_teachers ct ON ct.classroom_id = c.id
                    WHERE ct.teacher_id = ?
                    """.formatted(schema);
            countArgs = new Object[]{requesterId};
            dataSql = """
                    SELECT c.id, c.name, c.created_at
                    FROM %1$s.classrooms c
                    JOIN %1$s.classroom_teachers ct ON ct.classroom_id = c.id
                    WHERE ct.teacher_id = ?
                    ORDER BY c.name
                    LIMIT ? OFFSET ?
                    """.formatted(schema);
            dataArgs = new Object[]{requesterId, safeSize, offset};
        }

        Long total = jdbcTemplate.queryForObject(countSql, Long.class, countArgs);
        long totalElements = total != null ? total : 0;
        List<Map<String, Object>> content = jdbcTemplate.queryForList(dataSql, dataArgs);

        return com.learniq.common.api.PageResponse.of(content, totalElements, safePage, safeSize);
    }

    public Map<String, Object> getClassroom(String tenantId, UUID classroomId) {
        String schema = schema();
        var rows = jdbcTemplate.queryForList(
                "SELECT id, name, created_at FROM %s.classrooms WHERE id = ?".formatted(schema),
                classroomId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Classroom not found");
        }
        return rows.get(0);
    }

    // ── Teacher management ───────────────────────────────────────────────────

    public void addTeacher(String tenantId, UUID classroomId, UUID teacherId) {
        String schema = schema();
        assertClassroomExists(schema, classroomId);
        assertUserExists(schema, teacherId, "TEACHER");
        jdbcTemplate.update(
                "INSERT INTO %s.classroom_teachers (classroom_id, teacher_id) VALUES (?, ?) ON CONFLICT DO NOTHING"
                        .formatted(schema),
                classroomId, teacherId);
        log.info("Teacher {} added to classroom {} in tenant {}", teacherId, classroomId, tenantId);
    }

    public void removeTeacher(String tenantId, UUID classroomId, UUID teacherId) {
        String schema = schema();
        int rows = jdbcTemplate.update(
                "DELETE FROM %s.classroom_teachers WHERE classroom_id = ? AND teacher_id = ?".formatted(schema),
                classroomId, teacherId);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Teacher not found in this classroom");
        }
    }

    public List<Map<String, Object>> listTeachers(String tenantId, UUID classroomId) {
        String schema = schema();
        assertClassroomExists(schema, classroomId);
        return jdbcTemplate.queryForList(
                """
                SELECT u.id, u.username, u.full_name, u.email
                FROM %1$s.users u
                JOIN %1$s.classroom_teachers ct ON ct.teacher_id = u.id
                WHERE ct.classroom_id = ?
                ORDER BY u.full_name
                """.formatted(schema), classroomId);
    }

    // ── Student management ───────────────────────────────────────────────────

    public void addStudent(String tenantId, UUID classroomId, UUID studentId) {
        String schema = schema();
        assertClassroomExists(schema, classroomId);
        assertUserExists(schema, studentId, "STUDENT");
        jdbcTemplate.update(
                "INSERT INTO %s.classroom_students (classroom_id, student_id) VALUES (?, ?) ON CONFLICT DO NOTHING"
                        .formatted(schema),
                classroomId, studentId);
        log.info("Student {} added to classroom {} in tenant {}", studentId, classroomId, tenantId);
    }

    public void removeStudent(String tenantId, UUID classroomId, UUID studentId) {
        String schema = schema();
        int rows = jdbcTemplate.update(
                "DELETE FROM %s.classroom_students WHERE classroom_id = ? AND student_id = ?".formatted(schema),
                classroomId, studentId);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found in this classroom");
        }
    }

    public List<Map<String, Object>> listStudents(String tenantId, UUID classroomId) {
        String schema = schema();
        assertClassroomExists(schema, classroomId);
        return jdbcTemplate.queryForList(
                """
                SELECT u.id, u.username, u.full_name, u.email
                FROM %1$s.users u
                JOIN %1$s.classroom_students cs ON cs.student_id = u.id
                WHERE cs.classroom_id = ?
                ORDER BY u.full_name
                """.formatted(schema), classroomId);
    }

    // ── Guards ───────────────────────────────────────────────────────────────

    private void assertClassroomExists(String schema, UUID classroomId) {
        var rows = jdbcTemplate.queryForList(
                "SELECT 1 FROM %s.classrooms WHERE id = ?".formatted(schema), classroomId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Classroom not found");
        }
    }

    private void assertUserExists(String schema, UUID userId, String expectedRole) {
        var rows = jdbcTemplate.queryForList(
                "SELECT 1 FROM %s.users WHERE id = ? AND role = ? AND active = TRUE".formatted(schema),
                userId, expectedRole);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    expectedRole + " user not found or inactive");
        }
    }
}
