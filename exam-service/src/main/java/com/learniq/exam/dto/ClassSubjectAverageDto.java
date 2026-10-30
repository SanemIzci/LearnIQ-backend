package com.learniq.exam.dto;

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
public class ClassSubjectAverageDto {
    private UUID subjectId;
    private String subjectName;
    private BigDecimal avgCorrect;
    private BigDecimal avgWrong;
    private BigDecimal avgNet;
}
