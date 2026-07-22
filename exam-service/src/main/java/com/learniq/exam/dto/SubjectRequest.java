package com.learniq.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubjectRequest {

    @NotBlank(message = "Subject name cannot be blank")
    @Size(min = 2, max = 100, message = "Subject name must be between 2 and 100 characters")
    private String name;
}
