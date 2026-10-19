package com.learniq.exam.repository;

import com.learniq.exam.domain.ExamTypeSubjectRule;
import com.learniq.exam.domain.ExamTypeSubjectRuleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExamTypeSubjectRuleRepository extends JpaRepository<ExamTypeSubjectRule, ExamTypeSubjectRuleId> {
    List<ExamTypeSubjectRule> findByExamType(String examType);
}
