package com.learniq.exam.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Öğretmenin bir sınıf/ders/sınav için tuttuğu gözlem notu.
 * tags alanı JSONB olarak saklanır; Jackson serializasyon için String kullanılır.
 */
@Entity
@Table(name = "teacher_observations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class TeacherObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "classroom_id", nullable = false)
    private UUID classroomId;

    /** Ders ID (opsiyonel — genel sınıf notu ise null) */
    @Column(name = "subject_id")
    private UUID subjectId;

    /** Sınav ID (opsiyonel — genel not ise null) */
    @Column(name = "exam_id")
    private UUID examId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * JSONB etiketler; örn. '["motivasyon","dikkat"]'
     * Hibernate 6 ile @JdbcTypeCode(SqlTypes.JSON) kullanılıyor.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String tags;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
