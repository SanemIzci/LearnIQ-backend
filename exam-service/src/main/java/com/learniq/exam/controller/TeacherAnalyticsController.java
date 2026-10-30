package com.learniq.exam.controller;

import com.learniq.exam.dto.ClassroomAnalyticsResponse;
import com.learniq.exam.dto.ClassroomComparisonResponse;
import com.learniq.exam.service.TeacherAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teachers/me")
@RequiredArgsConstructor
public class TeacherAnalyticsController {

    private final TeacherAnalyticsService teacherAnalyticsService;

    /**
     * GET /api/v1/teachers/me/classrooms/{classroomId}/analytics
     * Returns success analytics (averages, subject breakdown, trends) for the specified classroom.
     */
    @GetMapping("/classrooms/{classroomId}/analytics")
    public ResponseEntity<ClassroomAnalyticsResponse> getClassroomAnalytics(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID classroomId,
            @RequestParam(value = "examId", required = false) UUID examId) {

        ClassroomAnalyticsResponse response = teacherAnalyticsService.getClassroomAnalytics(
                tenantId,
                UUID.fromString(userId),
                role,
                classroomId,
                examId);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/teachers/me/comparison?classroomIds=id1,id2&examId=optionalExamId
     * Compares multiple classrooms on a given exam.
     */
    @GetMapping("/comparison")
    public ResponseEntity<ClassroomComparisonResponse> compareClassrooms(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("classroomIds") List<UUID> classroomIds,
            @RequestParam(value = "examId", required = false) UUID examId) {

        ClassroomComparisonResponse response = teacherAnalyticsService.compareClassrooms(
                tenantId,
                UUID.fromString(userId),
                role,
                classroomIds,
                examId);

        return ResponseEntity.ok(response);
    }
}
