package com.learniq.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentObservationDto {
    private UUID observationId;
    private String classroomName;
    private String subjectName;
    private UUID teacherRef;
    private String tags;
    private String content;
    private LocalDateTime observedAt;
}
