package com.learniq.exam.service;

import com.learniq.exam.domain.ExamTypeSubjectRule;
import com.learniq.exam.domain.ExamTypeSubjectRuleId;
import com.learniq.exam.domain.Subject;
import com.learniq.exam.repository.ExamTypeSubjectRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NetCalculatorTest {

    @Mock
    private ExamTypeSubjectRuleRepository ruleRepository;

    @InjectMocks
    private NetCalculator netCalculator;

    private UUID subjectId;
    private ExamTypeSubjectRule tytRule;
    private ExamTypeSubjectRule lgsRule;

    @BeforeEach
    void setUp() {
        subjectId = UUID.randomUUID();
        Subject math = Subject.builder().id(subjectId).name("Matematik").build();

        tytRule = ExamTypeSubjectRule.builder()
                .examType("TYT")
                .subjectId(subjectId)
                .subject(math)
                .questionCount(40)
                .penaltyDivisor(BigDecimal.valueOf(4))
                .build();

        lgsRule = ExamTypeSubjectRule.builder()
                .examType("LGS")
                .subjectId(subjectId)
                .subject(math)
                .questionCount(20)
                .penaltyDivisor(BigDecimal.valueOf(3))
                .build();
    }

    private void stubRule(ExamTypeSubjectRule rule) {
        when(ruleRepository.findById(new ExamTypeSubjectRuleId(rule.getExamType(), subjectId)))
                .thenReturn(Optional.of(rule));
    }

    @Test
    @DisplayName("30 doğru / 4 yanlış (ceza 4) -> 29 net")
    void calculateNetScore_validInput_calculatesCorrectly() {
        stubRule(tytRule);

        BigDecimal net = netCalculator.calculateNetScore("TYT", subjectId, 30, 4);

        assertThat(net).isEqualByComparingTo(new BigDecimal("29"));
    }

    @Test
    @DisplayName("30 doğru / 5 yanlış (ceza 4) -> 28.75 net (ondalık)")
    void calculateNetScore_halfPenalty_calculatesDecimalsCorrectly() {
        stubRule(tytRule);

        BigDecimal net = netCalculator.calculateNetScore("TYT", subjectId, 30, 5);

        assertThat(net).isEqualByComparingTo(new BigDecimal("28.75"));
    }

    @Test
    @DisplayName("LGS ceza böleni 3: 10 doğru / 5 yanlış -> 5/3=1.67 -> 8.33 net (HALF_UP)")
    void calculateNetScore_lgsDivisor_roundsHalfUp() {
        stubRule(lgsRule);

        BigDecimal net = netCalculator.calculateNetScore("LGS", subjectId, 10, 5);

        assertThat(net).isEqualByComparingTo(new BigDecimal("8.33"));
    }

    @Test
    @DisplayName("0 doğru / 0 yanlış -> 0 net, istisna yok")
    void calculateNetScore_zeroInputs_returnsZero() {
        stubRule(tytRule);

        BigDecimal net = netCalculator.calculateNetScore("TYT", subjectId, 0, 0);

        assertThat(net).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Tam sınır: doğru + yanlış == soru sayısı -> izin verilir")
    void calculateNetScore_exactQuestionCount_isAllowed() {
        stubRule(tytRule);

        BigDecimal net = netCalculator.calculateNetScore("TYT", subjectId, 38, 2);

        assertThat(net).isEqualByComparingTo(new BigDecimal("37.5"));
    }

    @Test
    @DisplayName("Soru sayısı aşılırsa IllegalArgumentException")
    void calculateNetScore_exceedsQuestionCount_throwsException() {
        stubRule(tytRule);

        assertThatThrownBy(() -> netCalculator.calculateNetScore("TYT", subjectId, 35, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Toplam soru sayısı aşıldı");
    }

    @Test
    @DisplayName("Negatif doğru sayısı -> IllegalArgumentException (repo'ya hiç gidilmez)")
    void calculateNetScore_negativeCorrect_throwsException() {
        assertThatThrownBy(() -> netCalculator.calculateNetScore("TYT", subjectId, -5, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Negatif yanlış sayısı -> IllegalArgumentException")
    void calculateNetScore_negativeWrong_throwsException() {
        assertThatThrownBy(() -> netCalculator.calculateNetScore("TYT", subjectId, 0, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Ders kuralı tanımlı değilse IllegalStateException")
    void calculateNetScore_ruleMissing_throwsIllegalState() {
        when(ruleRepository.findById(new ExamTypeSubjectRuleId("TYT", subjectId)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> netCalculator.calculateNetScore("TYT", subjectId, 1, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Sınav kuralları eksik");
    }
}
