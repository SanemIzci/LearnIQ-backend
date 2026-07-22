package com.learniq.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class StudentExamResultRequest {

    @NotNull(message = "Student ID cannot be null")
    private UUID studentId;

    @NotNull(message = "Total score cannot be null")
    @DecimalMin(value = "0.0", inclusive = true, message = "Total score must be 0 or greater")
    private BigDecimal totalScore;

    @Min(value = 1, message = "Class rank must be at least 1")
    private Integer classRank;

    @Min(value = 1, message = "School rank must be at least 1")
    private Integer schoolRank;

    @Valid
    @NotNull(message = "Subject results list cannot be null")
    @Size(min = 1, message = "At least one subject result must be provided")
    private List<SubjectResultDto> subjectResults;
}
