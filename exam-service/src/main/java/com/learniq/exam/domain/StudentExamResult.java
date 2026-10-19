package com.learniq.exam.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "student_exam_results", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"student_id", "exam_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentExamResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @Column(name = "total_score", nullable = false, precision = 7, scale = 2)
    private BigDecimal totalScore;

    @Column(name = "class_rank")
    private Integer classRank;

    @Column(name = "school_rank")
    private Integer schoolRank;

    @Column(precision = 5, scale = 2)
    private BigDecimal percentile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ResultStatus status = ResultStatus.SUBMITTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_method", nullable = false, length = 20)
    @Builder.Default
    private EntryMethod entryMethod = EntryMethod.MANUAL;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum ResultStatus {
        SUBMITTED, APPROVED, REJECTED, DRAFT
    }

    public enum EntryMethod {
        MANUAL, OCR, TEACHER_BULK
    }
}
