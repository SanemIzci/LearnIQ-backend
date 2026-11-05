package com.learniq.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamResponse {
    private UUID id;
    private String title;
    private LocalDate examDate;
    private String examType;
    private List<SubjectResponse> subjects;
    @Builder.Default
    private List<UUID> classroomIds = new java.util.ArrayList<>();
    private LocalDateTime createdAt;
}
