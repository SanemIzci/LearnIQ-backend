package com.learniq.exam.repository;

import com.learniq.exam.domain.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    boolean existsByNameIgnoreCase(String name);
}
