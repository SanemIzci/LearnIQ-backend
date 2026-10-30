package com.learniq.exam.repository;

import com.learniq.exam.domain.TeacherObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeacherObservationRepository extends JpaRepository<TeacherObservation, UUID> {

    /** Öğretmenin tüm notlarını, en yeni önce */
    List<TeacherObservation> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId);

    /** Belirli sınıfa ait öğretmen notları */
    List<TeacherObservation> findByTeacherIdAndClassroomIdOrderByCreatedAtDesc(UUID teacherId, UUID classroomId);

    /** Sahiplik doğrulamalı tekil sorgu */
    Optional<TeacherObservation> findByIdAndTeacherId(UUID id, UUID teacherId);
}
