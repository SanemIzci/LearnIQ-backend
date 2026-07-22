package com.learniq.exam.controller;

import com.learniq.common.dto.StudentTrendReportDto;
import com.learniq.exam.service.AnalyticsEventPublisher;
import com.learniq.exam.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final AnalyticsEventPublisher analyticsEventPublisher;

    @GetMapping("/students/{studentId}/history")
    public ResponseEntity<StudentTrendReportDto> getStudentHistory(@PathVariable UUID studentId) {
        StudentTrendReportDto report = analyticsService.getStudentTrendReport(studentId);
        return ResponseEntity.ok(report);
    }

    @PostMapping("/students/{studentId}/reports/generate")
    public ResponseEntity<Void> generateAiReport(@PathVariable UUID studentId) {
        StudentTrendReportDto report = analyticsService.getStudentTrendReport(studentId);
        analyticsEventPublisher.publishAiReportRequest(studentId, report);
        return ResponseEntity.accepted().build();
    }
}
