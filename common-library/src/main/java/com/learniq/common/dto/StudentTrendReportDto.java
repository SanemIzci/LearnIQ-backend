package com.learniq.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentTrendReportDto {
    private UUID studentId;
    private int totalExamsTaken;
    private List<ExamPerformanceDto> examHistory;
}
