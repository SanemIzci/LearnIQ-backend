package com.learniq.exam.controller;

import com.learniq.exam.dto.StudentDashboardResponse;
import com.learniq.exam.service.ShareLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/share")
@RequiredArgsConstructor
public class PublicShareController {

    private final ShareLinkService shareLinkService;

    @GetMapping("/{token}")
    public ResponseEntity<StudentDashboardResponse> getSharedSnapshot(@PathVariable String token) {
        // Bu endpoint Gateway seviyesinde JWT doğrulaması dışında bırakılmıştır.
        try {
            return ResponseEntity.ok(shareLinkService.getSharedSnapshot(token));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
