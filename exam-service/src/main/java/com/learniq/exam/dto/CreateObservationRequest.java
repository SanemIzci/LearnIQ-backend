package com.learniq.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateObservationRequest {

    @NotBlank(message = "Gözlem içeriği boş olamaz")
    @Size(min = 10, max = 5000, message = "İçerik 10-5000 karakter arasında olmalıdır")
    private String content;

    /** Opsiyonel — sadece derse özgü notlar için */
    private UUID subjectId;

    /** Opsiyonel — sınava bağlı notlar için */
    private UUID examId;

    /** Opsiyonel etiketler; örn. ["motivasyon","dikkat"] */
    private List<String> tags;
}
