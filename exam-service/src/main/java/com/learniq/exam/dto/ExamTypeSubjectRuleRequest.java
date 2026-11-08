package com.learniq.exam.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ExamTypeSubjectRuleRequest {

    @NotNull(message = "Soru sayısı zorunludur")
    @Min(value = 1, message = "Soru sayısı en az 1 olmalıdır")
    private Integer questionCount;

    /** Boşsa examType'a göre varsayılan ceza böleni uygulanır (TYT: 4.0, LGS: 3.0, diğer: 4.0). */
    @DecimalMin(value = "0.1", message = "Ceza böleni en az 0.1 olmalıdır")
    private BigDecimal penaltyDivisor;
}
