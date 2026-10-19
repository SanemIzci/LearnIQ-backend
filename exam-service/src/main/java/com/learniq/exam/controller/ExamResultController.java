package com.learniq.exam.controller;

import com.learniq.exam.domain.StudentExamResult;
import com.learniq.exam.dto.ExamResultSubmitRequest;
import com.learniq.exam.dto.RejectionRequest;
import com.learniq.exam.repository.StudentExamResultRepository;
import com.learniq.exam.service.ExamResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exams/{examId}/results")
@RequiredArgsConstructor
public class ExamResultController {

    private final ExamResultService examResultService;
    private final StudentExamResultRepository studentExamResultRepository;

    @PostMapping("/me")
    public ResponseEntity<Void> submitMyResult(@PathVariable UUID examId,
                                               @RequestHeader("X-User-Id") String userId,
                                               @RequestHeader("X-User-Role") String role,
                                               @Valid @RequestBody ExamResultSubmitRequest request) {
        if (!"STUDENT".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        examResultService.submitResult(examId, UUID.fromString(userId), request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    public ResponseEntity<List<StudentExamResult>> getResultsByStatus(
            @PathVariable UUID examId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(defaultValue = "SUBMITTED") StudentExamResult.ResultStatus status) {
        
        if (!"ADMIN".equals(role) && !"TEACHER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(studentExamResultRepository.findByExamIdAndStatus(examId, status));
    }

    @PostMapping("/{resultId}/approve")
    public ResponseEntity<Void> approveResult(@PathVariable UUID examId,
                                              @PathVariable UUID resultId,
                                              @RequestHeader("X-User-Id") String userId,
                                              @RequestHeader("X-User-Role") String role) {
        if (!"ADMIN".equals(role) && !"TEACHER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        examResultService.approveResult(examId, resultId, UUID.fromString(userId));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{resultId}/reject")
    public ResponseEntity<Void> rejectResult(@PathVariable UUID examId,
                                             @PathVariable UUID resultId,
                                             @RequestHeader("X-User-Id") String userId,
                                             @RequestHeader("X-User-Role") String role,
                                             @Valid @RequestBody RejectionRequest request) {
        if (!"ADMIN".equals(role) && !"TEACHER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        examResultService.rejectResult(examId, resultId, UUID.fromString(userId), request.getReason());
        return ResponseEntity.ok().build();
    }
}
