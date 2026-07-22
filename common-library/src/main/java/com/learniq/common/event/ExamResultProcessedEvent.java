package com.learniq.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamResultProcessedEvent {
    private String tenantId;
    private UUID studentId;
    private UUID examId;
    private BigDecimal totalScore;
}
