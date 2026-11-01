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
public class SubjectScorecardRow {
    private UUID subjectId;
    private String subjectName;
    private int classroomCount;
    private BigDecimal avgNet;
    private BigDecimal minNet;
    private BigDecimal maxNet;
    private String bestClassroomName;
    private String worstClassroomName;
}
