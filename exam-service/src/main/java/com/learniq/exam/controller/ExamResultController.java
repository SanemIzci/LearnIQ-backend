package com.learniq.exam.controller;

import com.learniq.exam.dto.StudentExamResultRequest;
import com.learniq.exam.service.ExamResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exams")
@RequiredArgsConstructor
public class ExamResultController {

    private final ExamResultService examResultService;

    @PostMapping("/{examId}/results")
    public ResponseEntity<Void> submitExamResult(@PathVariable UUID examId,
                                                 @Valid @RequestBody StudentExamResultRequest request) {
        examResultService.submitExamResult(examId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
