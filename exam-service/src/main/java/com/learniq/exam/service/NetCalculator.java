package com.learniq.exam.service;

import com.learniq.exam.domain.ExamTypeSubjectRule;
import com.learniq.exam.domain.ExamTypeSubjectRuleId;
import com.learniq.exam.repository.ExamTypeSubjectRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NetCalculator {

    private final ExamTypeSubjectRuleRepository ruleRepository;

    /**
     * Soru sayısı kontrolü yapar ve formüle göre (Doğru - Yanlış/Ceza_Böleni) neti hesaplar.
     */
    public BigDecimal calculateNetScore(String examType, UUID subjectId, int correct, int wrong) {
        if (correct < 0 || wrong < 0) {
            throw new IllegalArgumentException("Doğru veya yanlış sayısı negatif olamaz.");
        }

        ExamTypeSubjectRule rule = ruleRepository.findById(new ExamTypeSubjectRuleId(examType, subjectId))
                .orElseThrow(() -> new IllegalStateException("Sınav kuralları eksik: " + examType + " - " + subjectId));

        if ((correct + wrong) > rule.getQuestionCount()) {
            throw new IllegalArgumentException(String.format(
                    "Toplam soru sayısı aşıldı! %s için maksimum soru sayısı: %d (Girilen: D:%d, Y:%d)",
                    rule.getSubject().getName(), rule.getQuestionCount(), correct, wrong));
        }

        BigDecimal wrongCount = BigDecimal.valueOf(wrong);
        BigDecimal penalty = wrongCount.divide(rule.getPenaltyDivisor(), 2, RoundingMode.HALF_UP);
        
        return BigDecimal.valueOf(correct).subtract(penalty);
    }
}
