package com.learniq.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamSummaryDto {
    private UUID id;
    private String title;
    private LocalDate examDate;
    private String examType;
}
