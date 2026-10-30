package com.learniq.exam.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class UpdateObservationRequest {

    @Size(min = 10, max = 5000, message = "İçerik 10-5000 karakter arasında olmalıdır")
    private String content;

    private UUID subjectId;
    private UUID examId;
    private List<String> tags;
}
