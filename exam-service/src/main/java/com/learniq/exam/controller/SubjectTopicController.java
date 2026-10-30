package com.learniq.exam.controller;

import com.learniq.exam.dto.CreateSubjectTopicRequest;
import com.learniq.exam.dto.SubjectTopicResponse;
import com.learniq.exam.dto.UpdateSubjectTopicRequest;
import com.learniq.exam.service.SubjectTopicService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subjects/{subjectId}/topics")
@RequiredArgsConstructor
public class SubjectTopicController {

    private final SubjectTopicService subjectTopicService;

    private void checkModificationPermission(String role) {
        // fail-closed: rol bilgisi yoksa/boşsa yazma işlemi reddedilir
        if (role == null || role.isBlank()
                || !"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role) && !"TEACHER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only teachers and administrators can modify subject topics.");
        }
    }

    /**
     * GET /api/v1/subjects/{subjectId}/topics
     * Returns all topics for the given subject.
     */
    @GetMapping
    public ResponseEntity<List<SubjectTopicResponse>> listTopics(@PathVariable UUID subjectId) {
        return ResponseEntity.ok(subjectTopicService.listTopicsBySubject(subjectId));
    }

    /**
     * GET /api/v1/subjects/{subjectId}/topics/{topicId}
     * Returns a single topic by ID.
     */
    @GetMapping("/{topicId}")
    public ResponseEntity<SubjectTopicResponse> getTopic(
            @PathVariable UUID subjectId,
            @PathVariable UUID topicId) {
        return ResponseEntity.ok(subjectTopicService.getTopic(subjectId, topicId));
    }

    /**
     * POST /api/v1/subjects/{subjectId}/topics
     * Creates a new topic for the subject (ADMIN or TEACHER).
     */
    @PostMapping
    public ResponseEntity<SubjectTopicResponse> createTopic(
            @PathVariable UUID subjectId,
            @Valid @RequestBody CreateSubjectTopicRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        checkModificationPermission(role);
        SubjectTopicResponse response = subjectTopicService.createTopic(subjectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * PUT /api/v1/subjects/{subjectId}/topics/{topicId}
     * Updates an existing topic (ADMIN or TEACHER).
     */
    @PutMapping("/{topicId}")
    public ResponseEntity<SubjectTopicResponse> updateTopic(
            @PathVariable UUID subjectId,
            @PathVariable UUID topicId,
            @Valid @RequestBody UpdateSubjectTopicRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        checkModificationPermission(role);
        SubjectTopicResponse response = subjectTopicService.updateTopic(subjectId, topicId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/v1/subjects/{subjectId}/topics/{topicId}
     * Deletes a topic (ADMIN or TEACHER).
     */
    @DeleteMapping("/{topicId}")
    public ResponseEntity<Void> deleteTopic(
            @PathVariable UUID subjectId,
            @PathVariable UUID topicId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        checkModificationPermission(role);
        subjectTopicService.deleteTopic(subjectId, topicId);
        return ResponseEntity.noContent().build();
    }
}
