package com.learniq.exam.service;

import com.learniq.exam.domain.ExamTypeSubjectRule;
import com.learniq.exam.domain.ExamTypeSubjectRuleId;
import com.learniq.exam.domain.Subject;
import com.learniq.exam.dto.ExamTypeSubjectRuleRequest;
import com.learniq.exam.dto.ExamTypeSubjectRuleResponse;
import com.learniq.exam.exception.EntityNotFoundException;
import com.learniq.exam.repository.ExamTypeSubjectRuleRepository;
import com.learniq.exam.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExamRuleService {

    private final ExamTypeSubjectRuleRepository ruleRepository;
    private final SubjectRepository subjectRepository;

    @Transactional(readOnly = true)
    public List<ExamTypeSubjectRuleResponse> listRules(String examType) {
        return ruleRepository.findByExamType(examType).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Net hesabı için gereken (examType, subjectId) kuralını oluşturur veya günceller.
     * Bu endpoint yoksa yeni tenant'ta sonuç gönderimi NetCalculator yüzünden 409 ile bloke edilir.
     */
    @Transactional
    public ExamTypeSubjectRuleResponse upsertRule(String examType, UUID subjectId, ExamTypeSubjectRuleRequest request) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new EntityNotFoundException("Subject", subjectId));

        BigDecimal penaltyDivisor = request.getPenaltyDivisor() != null
                ? request.getPenaltyDivisor()
                : defaultPenaltyDivisor(examType);

        ExamTypeSubjectRule rule = ruleRepository.findById(new ExamTypeSubjectRuleId(examType, subjectId))
                .orElseGet(() -> ExamTypeSubjectRule.builder()
                        .examType(examType)
                        .subjectId(subjectId)
                        .build());
        rule.setQuestionCount(request.getQuestionCount());
        rule.setPenaltyDivisor(penaltyDivisor);
        rule = ruleRepository.save(rule);

        return toBySubjectResponse(rule, subject);
    }

    private BigDecimal defaultPenaltyDivisor(String examType) {
        if ("LGS".equalsIgnoreCase(examType)) {
            return BigDecimal.valueOf(3.0);
        }
        return BigDecimal.valueOf(4.0);
    }

    private ExamTypeSubjectRuleResponse toResponse(ExamTypeSubjectRule rule) {
        return ExamTypeSubjectRuleResponse.builder()
                .examType(rule.getExamType())
                .subjectId(rule.getSubjectId())
                .subjectName(rule.getSubject() != null ? rule.getSubject().getName() : null)
                .questionCount(rule.getQuestionCount())
                .penaltyDivisor(rule.getPenaltyDivisor())
                .build();
    }

    private ExamTypeSubjectRuleResponse toBySubjectResponse(ExamTypeSubjectRule rule, Subject subject) {
        return ExamTypeSubjectRuleResponse.builder()
                .examType(rule.getExamType())
                .subjectId(rule.getSubjectId())
                .subjectName(subject.getName())
                .questionCount(rule.getQuestionCount())
                .penaltyDivisor(rule.getPenaltyDivisor())
                .build();
    }
}
