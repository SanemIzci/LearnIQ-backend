package com.learniq.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamPerformanceDto {
    private UUID examId;
    private String examTitle;
    private String examType;
    private LocalDate examDate;
    private BigDecimal totalScore;
    private Integer classRank;
    private Integer schoolRank;
    private List<SubjectNetScoreDto> subjectScores;
}
