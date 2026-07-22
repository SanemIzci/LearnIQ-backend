package com.learniq.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectNetScoreDto {
    private UUID subjectId;
    private String subjectName;
    private Integer correctCount;
    private Integer wrongCount;
    private BigDecimal netScore;
}
