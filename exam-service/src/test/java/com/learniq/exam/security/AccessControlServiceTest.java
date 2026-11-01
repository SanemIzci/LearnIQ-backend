package com.learniq.exam.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccessControlService Unit Tests")
class AccessControlServiceTest {

    private static final String TENANT = "akademi_1";
    private static final UUID TEACHER_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
    private static final UUID STUDENT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    private static final UUID OTHER_TEACHER_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000002");
    private static final UUID EXAM_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID CLASSROOM_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000001");

    @Mock
    private JdbcTemplate jdbcTemplate;

    private AccessControlService accessControlService;

    @BeforeEach
    void setUp() {
        accessControlService = new AccessControlService(jdbcTemplate);
    }

    private void stubTeacherAssignedToExam() {
        when(jdbcTemplate.queryForList(contains("classroom_teachers ct"), eq(Integer.class),
                eq(EXAM_ID), eq(TEACHER_ID))).thenReturn(List.of(1));
    }

    private void stubTeacherNotAssignedToExam() {
        when(jdbcTemplate.queryForList(contains("classroom_teachers ct"), eq(Integer.class),
                eq(EXAM_ID), eq(TEACHER_ID))).thenReturn(List.of());
    }

    // ── validateExamAccess ───────────────────────────────────────────────────

    @Test
    @DisplayName("validateExamAccess - TEACHER exam'in sınıf eşleşmelerine atanmışsa erişir")
    void validateExamAccess_assignedTeacher_passes() {
        stubTeacherAssignedToExam();

        assertThatCode(() -> accessControlService.validateExamAccess(
                TENANT, TEACHER_ID, "TEACHER", EXAM_ID)).doesNotThrowAnyException();
        verify(jdbcTemplate).queryForList(contains(TENANT + ".exam_classrooms"), eq(Integer.class),
                eq(EXAM_ID), eq(TEACHER_ID));
    }

    @Test
    @DisplayName("validateExamAccess - atanmamış TEACHER, sınıf eşleşmesi olmayan exam'e (sonuç-giriş akışı) erişir")
    void validateExamAccess_noClassroomMapping_passes() {
        stubTeacherNotAssignedToExam();
        when(jdbcTemplate.queryForList(contains("FROM " + TENANT + ".exam_classrooms"), eq(Integer.class),
                eq(EXAM_ID))).thenReturn(List.of());

        assertThatCode(() -> accessControlService.validateExamAccess(
                TENANT, TEACHER_ID, "TEACHER", EXAM_ID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateExamAccess - atanmamış TEACHER, sınıf eşleşmeli exam'e 403 alır")
    void validateExamAccess_mappedExam_unassignedTeacher_forbidden() {
        stubTeacherNotAssignedToExam();
        when(jdbcTemplate.queryForList(contains("FROM " + TENANT + ".exam_classrooms"), eq(Integer.class),
                eq(EXAM_ID))).thenReturn(List.of(1));

        assertThatThrownBy(() -> accessControlService.validateExamAccess(
                TENANT, TEACHER_ID, "TEACHER", EXAM_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", org.springframework.http.HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("validateExamAccess - STUDENT her zaman 403 alır")
    void validateExamAccess_student_forbidden() {
        assertThatThrownBy(() -> accessControlService.validateExamAccess(
                TENANT, STUDENT_ID, "STUDENT", EXAM_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", org.springframework.http.HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("validateExamAccess - bilinmeyen/boş rol 403 alır")
    void validateExamAccess_unknownOrBlankRole_forbidden() {
        assertThatThrownBy(() -> accessControlService.validateExamAccess(
                TENANT, TEACHER_ID, "NINJA", EXAM_ID))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> accessControlService.validateExamAccess(
                TENANT, TEACHER_ID, "  ", EXAM_ID))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ── validateClassroomAccess (şema-nitelikli SQL doğrulaması) ─────────────

    @Test
    @DisplayName("validateClassroomAccess - TEACHER sınıf sahipliği şema-nitelikli SQL ile kontrol edilir")
    void validateClassroomAccess_qualifiedSchemaSql() {
        when(jdbcTemplate.queryForList(contains(TENANT + ".classroom_teachers"), eq(Integer.class),
                eq(TEACHER_ID), eq(CLASSROOM_ID))).thenReturn(List.of(1));

        assertThatCode(() -> accessControlService.validateClassroomAccess(
                TENANT, TEACHER_ID, "TEACHER", CLASSROOM_ID)).doesNotThrowAnyException();

        verify(jdbcTemplate).queryForList(contains("FROM " + TENANT + ".classroom_teachers"),
                eq(Integer.class), eq(TEACHER_ID), eq(CLASSROOM_ID));
    }

    // ── validateStudentAccess ────────────────────────────────────────────────

    @Test
    @DisplayName("validateStudentAccess - STUDENT kendi verisine erişir")
    void validateStudentAccess_studentSelf_passes() {
        assertThatCode(() -> accessControlService.validateStudentAccess(
                TENANT, STUDENT_ID, "STUDENT", STUDENT_ID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateStudentAccess - TEACHER kendi sınıfındaki öğrenciye erişir")
    void validateStudentAccess_teacherOwnStudent_passes() {
        when(jdbcTemplate.queryForList(contains(TENANT + ".classroom_students cs"), eq(Integer.class),
                eq(TEACHER_ID), eq(STUDENT_ID))).thenReturn(List.of(1));

        assertThatCode(() -> accessControlService.validateStudentAccess(
                TENANT, TEACHER_ID, "TEACHER", STUDENT_ID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateStudentAccess - TEACHER başka sınıftaki öğrenciye 403 alır")
    void validateStudentAccess_teacherForeignStudent_forbidden() {
        when(jdbcTemplate.queryForList(contains("classroom_students"), eq(Integer.class),
                eq(TEACHER_ID), eq(STUDENT_ID))).thenReturn(List.of());

        assertThatThrownBy(() -> accessControlService.validateStudentAccess(
                TENANT, TEACHER_ID, "TEACHER", STUDENT_ID))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("isTeacherAssignedToClassroom - atama satırına göre true/false döner")
    void isTeacherAssignedToClassroom_boolean() {
        when(jdbcTemplate.queryForList(contains(TENANT + ".classroom_teachers"), eq(Integer.class),
                eq(TEACHER_ID), eq(CLASSROOM_ID))).thenReturn(List.of(1));
        assertThat(accessControlService.isTeacherAssignedToClassroom(TENANT, TEACHER_ID, CLASSROOM_ID)).isTrue();

        when(jdbcTemplate.queryForList(contains(TENANT + ".classroom_teachers"), eq(Integer.class),
                eq(OTHER_TEACHER_ID), eq(CLASSROOM_ID))).thenReturn(List.of());
        assertThat(accessControlService.isTeacherAssignedToClassroom(TENANT, OTHER_TEACHER_ID, CLASSROOM_ID)).isFalse();
    }
}
