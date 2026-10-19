package com.learniq.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ExamResultSubmitRequest {
    @Valid
    @NotNull(message = "Ders listesi boş olamaz")
    @Size(min = 1, message = "En az bir ders için sonuç girmelisiniz")
    private List<SubjectInputDto> subjects;
}
