package com.learniq.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassExamTrendDto {
    private UUID examId;
    private String examTitle;
    private LocalDate examDate;
    private BigDecimal avgScore;
    private int studentCount;
}
