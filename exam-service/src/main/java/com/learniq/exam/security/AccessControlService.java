package com.learniq.exam.security;

import com.learniq.common.model.UserRole;
import com.learniq.exam.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Centralised access-control decisions for RBAC.
 *
 * ADMIN  → can access any student's data within the tenant
 * TEACHER → can only access students enrolled in their classroom
 * STUDENT → can only access their own data
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessControlService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Throws 403 Forbidden if the caller is not allowed to access the given studentId.
     */
    public void validateStudentAccess(String tenantId, UUID requesterId, String rawRole, UUID targetStudentId) {
        UserRole role;
        try {
            role = UserRole.valueOf(rawRole);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unknown role: " + rawRole);
        }

        switch (role) {
            case SUPER_ADMIN -> { /* Platform-wide access — no restrictions */ }

            case ADMIN -> { /* Tenant ADMIN sees all data within their tenant */ }

            case TEACHER -> {
                // Teacher can only access students in their classroom
                if (!isStudentInTeacherClassroom(tenantId, requesterId, targetStudentId)) {
                    log.warn("TEACHER {} tried to access student {} not in their classroom", requesterId, targetStudentId);
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "You do not have access to this student's data");
                }
            }

            case STUDENT -> {
                // Student can only access their own data
                if (!requesterId.equals(targetStudentId)) {
                    log.warn("STUDENT {} tried to access student {}", requesterId, targetStudentId);
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Students can only access their own data");
                }
            }
        }
    }

    /**
     * Throws 403 Forbidden if the caller is not allowed to access the given classroomId.
     * ADMIN / SUPER_ADMIN -> full access
     * TEACHER -> only classrooms assigned to them in classroom_teachers
     * STUDENT -> forbidden
     */
    public void validateClassroomAccess(String tenantId, UUID requesterId, String rawRole, UUID classroomId) {
        if (rawRole == null || rawRole.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing user role");
        }

        UserRole role;
        try {
            role = UserRole.valueOf(rawRole);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unknown role: " + rawRole);
        }

        switch (role) {
            case SUPER_ADMIN, ADMIN -> {
                // Tenant ADMIN and platform SUPER_ADMIN have full access
            }
            case TEACHER -> {
                if (!isTeacherAssignedToClassroom(tenantId, requesterId, classroomId)) {
                    log.warn("TEACHER {} tried to access classroom {} not assigned to them", requesterId, classroomId);
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "You do not have access to this classroom");
                }
            }
            case STUDENT -> {
                log.warn("STUDENT {} tried to access classroom analytics for classroom {}", requesterId, classroomId);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Students cannot access classroom analytics");
            }
        }
    }

    public boolean isTeacherAssignedToClassroom(String tenantId, UUID teacherId, UUID classroomId) {
        jdbcTemplate.execute("SET search_path TO " + com.learniq.common.tenant.TenantSchemaValidator.requireValid(tenantId));
        List<Integer> result = jdbcTemplate.queryForList(
                """
                SELECT 1
                FROM classroom_teachers
                WHERE teacher_id = ?
                  AND classroom_id = ?
                LIMIT 1
                """,
                Integer.class, teacherId, classroomId);
        return !result.isEmpty();
    }

    /**
     * Centralised access-control decisions for RBAC.
     *
     * SUPER_ADMIN → platform-wide, no restrictions
     * ADMIN       → all data within their tenant
     * TEACHER     → only students in ANY of their classrooms (N:N via classroom_teachers)
     * STUDENT     → only their own data
     */
    private boolean isStudentInTeacherClassroom(String tenantId, UUID teacherId, UUID studentId) {
        jdbcTemplate.execute("SET search_path TO " + com.learniq.common.tenant.TenantSchemaValidator.requireValid(tenantId));
        List<Integer> result = jdbcTemplate.queryForList(
                """
                SELECT 1
                FROM classroom_students cs
                JOIN classroom_teachers ct ON ct.classroom_id = cs.classroom_id
                WHERE ct.teacher_id = ?
                  AND cs.student_id = ?
                LIMIT 1
                """,
                Integer.class, teacherId, studentId);
        return !result.isEmpty();
    }
}
