package com.learniq.exam.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class StudentHomeResponseDto {
    private String studentName;
    private String classroomName;
    private LatestExamDto latestExam;
    private List<BadgeDto> recentBadges;

    @Data
    @Builder
    public static class LatestExamDto {
        private UUID examId;
        private String title;
        private LocalDate examDate;
        private BigDecimal netScore;
        private BigDecimal percentile;
    }

    @Data
    @Builder
    public static class BadgeDto {
        private String code;
        private String title;
        private String description;
        private String examTitle;
        private LocalDateTime awardedAt;
    }
}
