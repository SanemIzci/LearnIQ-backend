package com.learniq.exam.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RejectionRequest {
    @NotBlank(message = "Reddetme sebebi belirtilmelidir")
    private String reason;
}
