package com.learniq.exam.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.exam.service.ClassroomService;
import com.learniq.exam.security.AccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("ClassroomController Access-Control Integration Tests (standalone)")
class ClassroomControllerAccessTest {

    private static final UUID CLASSROOM_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000001");
    private static final UUID TEACHER_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");

    private ClassroomService classroomService;
    private AccessControlService accessControlService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        classroomService = mock(ClassroomService.class);
        accessControlService = mock(AccessControlService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ClassroomController(classroomService, accessControlService)).build();
    }

    @Test
    @DisplayName("GET /classrooms/{id} - atanmış TEACHER 200 alır ve sahiplik kontrolü çağrılır")
    void getClassroom_assignedTeacher_ok() throws Exception {
        when(classroomService.getClassroom("akademi_1", CLASSROOM_ID))
                .thenReturn(Map.of("id", CLASSROOM_ID.toString(), "name", "12-A"));

        mockMvc.perform(get("/api/v1/classrooms/{id}", CLASSROOM_ID)
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Id", TEACHER_ID.toString())
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk());

        verify(accessControlService).validateClassroomAccess(
                eq("akademi_1"), eq(TEACHER_ID), eq("TEACHER"), eq(CLASSROOM_ID));
    }

    @Test
    @DisplayName("GET /classrooms/{id} - atanmamış TEACHER 403 alır, servis çağrılmaz")
    void getClassroom_unassignedTeacher_forbidden() throws Exception {
        doThrow(new ResponseStatusException(FORBIDDEN, "You do not have access to this classroom"))
                .when(accessControlService)
                .validateClassroomAccess(any(), any(), any(), any());

        mockMvc.perform(get("/api/v1/classrooms/{id}", CLASSROOM_ID)
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Id", TEACHER_ID.toString())
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(classroomService);
    }

    @Test
    @DisplayName("GET /classrooms/{id}/students - STUDENT 403 alır (rol guard fail-closed)")
    void listStudents_studentRole_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/classrooms/{id}/students", CLASSROOM_ID)
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Id", "cccccccc-0000-0000-0000-000000000001")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(classroomService);
    }

    @Test
    @DisplayName("GET /classrooms/{id}/teachers - ADMIN sahiplik kontrolünden geçip listeyi alır")
    void listTeachers_admin_ok() throws Exception {
        when(classroomService.listTeachers("akademi_1", CLASSROOM_ID))
                .thenReturn(List.of(Map.of("teacherId", TEACHER_ID.toString())));

        mockMvc.perform(get("/api/v1/classrooms/{id}/teachers", CLASSROOM_ID)
                        .header("X-Tenant-Id", "akademi_1")
                        .header("X-User-Id", "11111111-0000-0000-0000-000000000001")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
