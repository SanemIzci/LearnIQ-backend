package com.learniq.exam.repository;

import com.learniq.exam.domain.Badge;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BadgeRepository extends JpaRepository<Badge, String> {
}
