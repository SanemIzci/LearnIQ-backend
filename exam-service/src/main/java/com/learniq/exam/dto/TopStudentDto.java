package com.learniq.exam.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TopStudentDto {
    private Integer rank;
    private String displayName; // "Ali Y." if consent given, else null or "Anonim"
    private BigDecimal netScore;
}
