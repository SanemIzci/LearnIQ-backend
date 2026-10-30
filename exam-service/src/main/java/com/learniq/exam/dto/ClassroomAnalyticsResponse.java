package com.learniq.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomAnalyticsResponse {
    private UUID classroomId;
    private String classroomName;
    private int totalStudents;
    private ExamSummaryDto analyzedExam;
    private int participationCount;
    private BigDecimal participationRate;
    private BigDecimal classAverageScore;
    private List<ClassSubjectAverageDto> subjectAverages;
    private List<ClassExamTrendDto> recentExamTrends;
}
