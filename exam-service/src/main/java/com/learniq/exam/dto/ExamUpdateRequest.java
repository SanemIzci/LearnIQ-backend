package com.learniq.exam.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ExamUpdateRequest {

    // All fields are optional — only non-null values will be applied (PATCH semantics)

    @Size(min = 3, max = 255, message = "Exam title must be between 3 and 255 characters")
    private String title;

    private LocalDate examDate;

    @Size(max = 50, message = "Exam type must be at most 50 characters")
    private String examType;
}
