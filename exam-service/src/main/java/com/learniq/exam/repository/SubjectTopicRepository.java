package com.learniq.exam.repository;

import com.learniq.exam.domain.SubjectTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubjectTopicRepository extends JpaRepository<SubjectTopic, UUID> {

    List<SubjectTopic> findBySubjectIdOrderByOrderIndexAscNameAsc(UUID subjectId);

    Optional<SubjectTopic> findByIdAndSubjectId(UUID id, UUID subjectId);

    boolean existsBySubjectIdAndNameIgnoreCase(UUID subjectId, String name);

    boolean existsBySubjectIdAndNameIgnoreCaseAndIdNot(UUID subjectId, String name, UUID id);
}
