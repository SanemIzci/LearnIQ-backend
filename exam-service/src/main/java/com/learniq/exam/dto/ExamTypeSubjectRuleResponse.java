package com.learniq.exam.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class ExamTypeSubjectRuleResponse {
    private String examType;
    private UUID subjectId;
    private String subjectName;
    private Integer questionCount;
    private BigDecimal penaltyDivisor;
}
