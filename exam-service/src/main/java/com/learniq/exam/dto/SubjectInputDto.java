package com.learniq.exam.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class SubjectInputDto {
    @NotNull(message = "Ders ID zorunludur")
    private UUID subjectId;

    @NotNull(message = "Doğru sayısı zorunludur")
    @Min(value = 0, message = "Doğru sayısı negatif olamaz")
    private Integer correctCount;

    @NotNull(message = "Yanlış sayısı zorunludur")
    @Min(value = 0, message = "Yanlış sayısı negatif olamaz")
    private Integer wrongCount;
}
