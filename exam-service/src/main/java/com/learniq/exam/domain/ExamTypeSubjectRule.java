package com.learniq.exam.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "exam_type_subject_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(ExamTypeSubjectRuleId.class)
public class ExamTypeSubjectRule {

    @Id
    @Column(name = "exam_type", length = 50)
    private String examType;

    @Id
    @Column(name = "subject_id")
    private UUID subjectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", insertable = false, updatable = false)
    private Subject subject;

    @Column(name = "question_count", nullable = false)
    private Integer questionCount;

    @Column(name = "penalty_divisor", nullable = false, precision = 3, scale = 1)
    private BigDecimal penaltyDivisor;
}
