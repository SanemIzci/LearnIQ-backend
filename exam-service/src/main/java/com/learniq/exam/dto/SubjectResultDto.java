package com.learniq.exam.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class SubjectResultDto {

    @NotNull(message = "Subject ID cannot be null")
    private UUID subjectId;

    @NotNull(message = "Correct count cannot be null")
    private Integer correctCount;

    @NotNull(message = "Wrong count cannot be null")
    private Integer wrongCount;

    @NotNull(message = "Net score cannot be null")
    private BigDecimal netScore;
}
