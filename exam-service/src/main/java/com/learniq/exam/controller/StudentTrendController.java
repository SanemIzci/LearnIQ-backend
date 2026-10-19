package com.learniq.exam.controller;

import com.learniq.exam.dto.TrendDataPointDto;
import com.learniq.exam.service.StudentTrendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/students/me/trends")
@RequiredArgsConstructor
public class StudentTrendController {

    private final StudentTrendService studentTrendService;

    @GetMapping
    public ResponseEntity<List<TrendDataPointDto>> getTrends(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) UUID subjectId) {

        if (!"STUDENT".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        UUID studentId = UUID.fromString(userId);

        if (subjectId != null) {
            return ResponseEntity.ok(studentTrendService.getSubjectTrends(studentId, subjectId));
        } else {
            return ResponseEntity.ok(studentTrendService.getOverallTrends(studentId));
        }
    }
}
