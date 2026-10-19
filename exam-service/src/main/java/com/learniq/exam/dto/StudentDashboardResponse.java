package com.learniq.exam.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class StudentDashboardResponse {
    private Integer classRank;
    private Integer schoolRank;
    private BigDecimal netScore;
    private BigDecimal percentile;
    private BigDecimal classAverage;
    private List<TopStudentDto> top3Students;
}
