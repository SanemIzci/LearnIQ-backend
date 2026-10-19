package com.learniq.exam.controller;

import com.learniq.exam.domain.Exam;
import com.learniq.exam.repository.ExamRepository;
import com.learniq.exam.dto.StudentDashboardResponse;
import com.learniq.exam.service.StudentDashboardService;
import com.learniq.exam.service.AiReportService;
import com.learniq.exam.service.ShareLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/students/me/exams")
@RequiredArgsConstructor
public class StudentExamController {

    private final ExamRepository examRepository;
    private final StudentDashboardService studentDashboardService;
    private final ShareLinkService shareLinkService;
    private final AiReportService aiReportService;

    @GetMapping
    public ResponseEntity<List<Exam>> getMyExams(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role) {

        if (!"STUDENT".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        List<Exam> exams = examRepository.findExamsForStudent(
                UUID.fromString(userId), Exam.ExamStatus.OPEN.name());
        
        return ResponseEntity.ok(exams);
    }

    @GetMapping("/{examId}/dashboard")
    public ResponseEntity<StudentDashboardResponse> getExamDashboard(
            @PathVariable UUID examId,
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestHeader("X-User-Role") String role) {
            
        if (!"STUDENT".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        UUID studentId = UUID.fromString(userIdHeader);
        return ResponseEntity.ok(studentDashboardService.getDashboard(examId, studentId));
    }

    @PostMapping("/{examId}/share")
    public ResponseEntity<Map<String, String>> generateShareLink(
            @PathVariable UUID examId,
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestHeader("X-User-Role") String role) {
        
        if (!"STUDENT".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        UUID studentId = UUID.fromString(userIdHeader);
        String token = shareLinkService.generateShareLink(examId, studentId);
        
        return ResponseEntity.ok(Map.of("token", token));
    }

    @PostMapping("/{examId}/ai-report")
    public ResponseEntity<com.learniq.exam.dto.AiReportResponseDto> requestAiReport(
            @PathVariable UUID examId,
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestHeader("X-User-Role") String role) {
            
        if (!"STUDENT".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        UUID studentId = UUID.fromString(userIdHeader);
        return ResponseEntity.ok(aiReportService.requestReport(examId, studentId));
    }
}
