package com.learniq.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassSubjectTrendRow {
    private String classroomName;
    private String subjectName;
    private String examTitle;
    private LocalDate examDate;
    private BigDecimal avgNet;
    private BigDecimal deltaFromPrevious;
    private String trendDirection;
}
