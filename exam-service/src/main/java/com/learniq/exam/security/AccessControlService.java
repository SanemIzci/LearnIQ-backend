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

    private boolean isStudentInTeacherClassroom(String tenantId, UUID teacherId, UUID studentId) {
        jdbcTemplate.execute("SET search_path TO " + tenantId);
        List<Integer> result = jdbcTemplate.queryForList(
                """
                SELECT 1 FROM classroom_students cs
                JOIN classrooms c ON c.id = cs.classroom_id
                WHERE c.teacher_id = ? AND cs.student_id = ?
                LIMIT 1
                """,
                Integer.class, teacherId, studentId);
        return !result.isEmpty();
    }
}
