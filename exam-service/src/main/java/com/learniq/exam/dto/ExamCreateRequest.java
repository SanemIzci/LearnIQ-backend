package com.learniq.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ExamCreateRequest {

    @NotBlank(message = "Exam title cannot be blank")
    @Size(min = 3, max = 255, message = "Exam title must be between 3 and 255 characters")
    private String title;

    @NotNull(message = "Exam date cannot be null")
    private LocalDate examDate;

    @NotBlank(message = "Exam type cannot be blank")
    @Size(max = 50, message = "Exam type must be at most 50 characters")
    private String examType; // e.g., TYT, AYT, LGS
}
