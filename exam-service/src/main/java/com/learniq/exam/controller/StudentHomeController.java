package com.learniq.exam.controller;

import com.learniq.exam.dto.StudentHomeResponseDto;
import com.learniq.exam.service.StudentHomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/students/me/home")
@RequiredArgsConstructor
public class StudentHomeController {

    private final StudentHomeService studentHomeService;

    @GetMapping
    public ResponseEntity<StudentHomeResponseDto> getHomeData(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role) {

        if (!"STUDENT".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        UUID studentId = UUID.fromString(userId);
        return ResponseEntity.ok(studentHomeService.getStudentHomeData(studentId));
    }
}
