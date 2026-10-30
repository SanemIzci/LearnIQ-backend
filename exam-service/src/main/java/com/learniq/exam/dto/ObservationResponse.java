package com.learniq.exam.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ObservationResponse {

    private UUID id;
    private UUID teacherId;
    private UUID classroomId;
    private UUID subjectId;
    private UUID examId;
    private String content;
    private List<String> tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
