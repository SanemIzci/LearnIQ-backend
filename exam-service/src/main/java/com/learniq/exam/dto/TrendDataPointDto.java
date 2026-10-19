package com.learniq.exam.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class TrendDataPointDto {
    private UUID examId;
    private String examName;
    private LocalDate examDate;
    private BigDecimal netScore;
    private BigDecimal percentile;
}
