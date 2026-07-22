package com.learniq.exam.repository;

import com.learniq.exam.domain.StudentExamResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StudentExamResultRepository extends JpaRepository<StudentExamResult, UUID> {

    /**
     * Fetch all exam results for a given student, ordered chronologically by exam date.
     * Uses a JOIN FETCH on the Exam entity to avoid N+1 queries.
     */
    @Query("""
            SELECT r FROM StudentExamResult r
            JOIN FETCH r.exam e
            WHERE r.studentId = :studentId
            ORDER BY e.examDate ASC
            """)
    List<StudentExamResult> findByStudentIdOrderByExamDateAsc(@Param("studentId") UUID studentId);
}
