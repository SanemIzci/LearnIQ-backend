package com.learniq.exam.repository;

import com.learniq.exam.domain.StudentBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StudentBadgeRepository extends JpaRepository<StudentBadge, UUID> {

    boolean existsByStudentIdAndBadgeCodeAndExamId(UUID studentId, String badgeCode, UUID examId);

    @Query("SELECT sb FROM StudentBadge sb JOIN FETCH sb.badge LEFT JOIN FETCH sb.exam WHERE sb.studentId = :studentId ORDER BY sb.awardedAt DESC")
    List<StudentBadge> findLatestBadgesByStudentId(@Param("studentId") UUID studentId);
}
