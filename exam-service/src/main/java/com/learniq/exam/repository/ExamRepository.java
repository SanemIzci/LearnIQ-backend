package com.learniq.exam.repository;

import com.learniq.exam.domain.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamRepository extends JpaRepository<Exam, UUID> {

    /**
     * Fetch all exams sorted by exam date descending (most recent first).
     * Uses JOIN FETCH to eagerly load subjects in a single query,
     * preventing the N+1 problem when mapping ExamResponse.
     */
    @Query("SELECT e FROM Exam e LEFT JOIN FETCH e.subjects ORDER BY e.examDate DESC")
    List<Exam> findAllWithSubjectsOrderByDateDesc();

    /**
     * Fetch a single exam with its subjects eagerly loaded.
     * Required because @ManyToMany is FetchType.LAZY by default.
     */
    @Query("SELECT e FROM Exam e LEFT JOIN FETCH e.subjects WHERE e.id = :id")
    Optional<Exam> findByIdWithSubjects(UUID id);
}
