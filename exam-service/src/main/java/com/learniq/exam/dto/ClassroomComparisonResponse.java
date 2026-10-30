package com.learniq.exam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomComparisonResponse {
    private UUID examId;
    private String examTitle;
    private LocalDate examDate;
    private List<ClassroomComparisonItemDto> classrooms;
}
