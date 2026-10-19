package com.learniq.exam.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiReportResponseDto {
    private String id;
    private String status; // PENDING, READY, FAILED
    private String summary;
    private String s3Key;
}
