package com.learniq.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSubjectTopicRequest {

    @NotBlank(message = "Konu adı boş olamaz")
    @Size(max = 150, message = "Konu adı en fazla 150 karakter olabilir")
    private String name;

    @Size(max = 50, message = "Konu kodu en fazla 50 karakter olabilir")
    private String code;

    private Integer orderIndex;
}
