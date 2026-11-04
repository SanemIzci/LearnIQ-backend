package com.learniq.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopStudentDto {
    private Integer rank;
    private String displayName; // "Ali Y." if consent given, else null or "Anonim"
    private BigDecimal netScore;
}
