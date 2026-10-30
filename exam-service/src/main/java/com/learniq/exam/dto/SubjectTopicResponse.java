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
public class SubjectTopicResponse {

    private UUID id;
    private UUID subjectId;
    private String name;
    private String code;
    private Integer orderIndex;
    private LocalDateTime createdAt;
}
