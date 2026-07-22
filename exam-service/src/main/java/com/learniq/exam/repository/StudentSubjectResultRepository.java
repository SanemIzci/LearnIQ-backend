package com.learniq.exam.repository;

import com.learniq.exam.domain.StudentSubjectResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StudentSubjectResultRepository extends JpaRepository<StudentSubjectResult, UUID> {

    /**
     * Fetch all subject-level results for a student in a specific exam.
     * Uses a JOIN FETCH on Subject to resolve names without extra queries.
     */
    @Query("""
            SELECT r FROM StudentSubjectResult r
            JOIN FETCH r.subject
            WHERE r.studentId = :studentId AND r.exam.id = :examId
            """)
    List<StudentSubjectResult> findByStudentIdAndExamId(@Param("studentId") UUID studentId,
                                                        @Param("examId") UUID examId);
}
