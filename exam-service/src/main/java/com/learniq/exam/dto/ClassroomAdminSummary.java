package com.learniq.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomAdminSummary {
    private java.util.UUID classroomId;
    private String classroomName;
    private java.util.UUID latestExamId;
    private String latestExamTitle;
    private java.time.LocalDate latestExamDate;
    private java.math.BigDecimal avgNet;
    private java.math.BigDecimal participationRate;
    private String bestSubjectName;
    private java.math.BigDecimal bestSubjectNet;
    private String worstSubjectName;
    private java.math.BigDecimal worstSubjectNet;
    private int upTrendCount;
    private int downTrendCount;
    private int stableTrendCount;
}
