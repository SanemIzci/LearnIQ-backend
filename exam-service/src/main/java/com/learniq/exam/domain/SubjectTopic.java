package com.learniq.exam.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "subject_topics",
        uniqueConstraints = @UniqueConstraint(name = "uk_subject_topics_subject_name", columnNames = {"subject_id", "name"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class SubjectTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 50)
    private String code;

    @Builder.Default
    @Column(name = "order_index", nullable = false)
    private Integer orderIndex = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
