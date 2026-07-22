package com.learniq.common.event;

import com.learniq.common.dto.StudentTrendReportDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiReportRequestedEvent {
    private String tenantId;
    private UUID studentId;
    private StudentTrendReportDto trendData;
}
