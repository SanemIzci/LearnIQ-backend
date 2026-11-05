package com.learniq.exam.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AiReportResponseDto {
    private String id;
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED
    private String summary;
    private String s3Key;
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
}
