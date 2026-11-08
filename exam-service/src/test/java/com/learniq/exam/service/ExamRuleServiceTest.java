package com.learniq.exam.service;

import com.learniq.exam.domain.ExamTypeSubjectRule;
import com.learniq.exam.domain.Subject;
import com.learniq.exam.dto.ExamTypeSubjectRuleRequest;
import com.learniq.exam.dto.ExamTypeSubjectRuleResponse;
import com.learniq.exam.exception.EntityNotFoundException;
import com.learniq.exam.repository.ExamTypeSubjectRuleRepository;
import com.learniq.exam.repository.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamRuleServiceTest {

    @Mock private ExamTypeSubjectRuleRepository ruleRepository;
    @Mock private SubjectRepository subjectRepository;

    @InjectMocks private ExamRuleService examRuleService;

    private UUID subjectId;
    private Subject subject;

    @BeforeEach
    void setUp() {
        subjectId = UUID.randomUUID();
        subject = Subject.builder().id(subjectId).name("Matematik").build();
    }

    private ExamTypeSubjectRuleRequest request(Integer questionCount, BigDecimal penaltyDivisor) {
        ExamTypeSubjectRuleRequest request = new ExamTypeSubjectRuleRequest();
        request.setQuestionCount(questionCount);
        request.setPenaltyDivisor(penaltyDivisor);
        return request;
    }

    @Test
    @DisplayName("Kural yoksa oluşturulur; ceza böleni examType'a göre varsayılan gelir")
    void upsertRule_createsWithDefaultPenalty() {
        when(subjectRepository.findById(subjectId)).thenReturn(Optional.of(subject));
        when(ruleRepository.findById(any())).thenReturn(Optional.empty());
        when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExamTypeSubjectRuleResponse response = examRuleService.upsertRule(
                "TYT", subjectId, request(40, null));

        ArgumentCaptor<ExamTypeSubjectRule> captor = ArgumentCaptor.forClass(ExamTypeSubjectRule.class);
        verify(ruleRepository).save(captor.capture());
        ExamTypeSubjectRule saved = captor.getValue();
        assertThat(saved.getExamType()).isEqualTo("TYT");
        assertThat(saved.getSubjectId()).isEqualTo(subjectId);
        assertThat(saved.getQuestionCount()).isEqualTo(40);
        assertThat(saved.getPenaltyDivisor()).isEqualByComparingTo(BigDecimal.valueOf(4.0));

        assertThat(response.getSubjectName()).isEqualTo("Matematik");
    }

    @Test
    @DisplayName("LGS için varsayılan ceza böleni 3.0'dır")
    void upsertRule_lgsDefaultPenalty() {
        when(subjectRepository.findById(subjectId)).thenReturn(Optional.of(subject));
        when(ruleRepository.findById(any())).thenReturn(Optional.empty());
        when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        examRuleService.upsertRule("LGS", subjectId, request(20, null));

        ArgumentCaptor<ExamTypeSubjectRule> captor = ArgumentCaptor.forClass(ExamTypeSubjectRule.class);
        verify(ruleRepository).save(captor.capture());
        assertThat(captor.getValue().getPenaltyDivisor()).isEqualByComparingTo(BigDecimal.valueOf(3.0));
    }

    @Test
    @DisplayName("Var olan kural güncellenir (questionCount ve penaltyDivisor ezilir)")
    void upsertRule_updatesExisting() {
        ExamTypeSubjectRule existing = ExamTypeSubjectRule.builder()
                .examType("TYT").subjectId(subjectId)
                .questionCount(40).penaltyDivisor(BigDecimal.valueOf(4.0))
                .build();
        when(subjectRepository.findById(subjectId)).thenReturn(Optional.of(subject));
        when(ruleRepository.findById(new com.learniq.exam.domain.ExamTypeSubjectRuleId("TYT", subjectId)))
                .thenReturn(Optional.of(existing));
        when(ruleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        examRuleService.upsertRule("TYT", subjectId, request(60, BigDecimal.valueOf(3.0)));

        assertThat(existing.getQuestionCount()).isEqualTo(60);
        assertThat(existing.getPenaltyDivisor()).isEqualByComparingTo(BigDecimal.valueOf(3.0));
        verify(ruleRepository).save(existing);
    }

    @Test
    @DisplayName("Subject bulunamazsa 404 üretir")
    void upsertRule_subjectNotFound_throwsEntityNotFound() {
        when(subjectRepository.findById(subjectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> examRuleService.upsertRule("TYT", subjectId, request(10, null)))
                .isInstanceOf(EntityNotFoundException.class);

        verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("listRules examType'a göre listeler")
    void listRules_returnsMappedList() {
        ExamTypeSubjectRule rule = ExamTypeSubjectRule.builder()
                .examType("TYT").subjectId(subjectId)
                .questionCount(40).penaltyDivisor(BigDecimal.valueOf(4.0))
                .subject(subject)
                .build();
        when(ruleRepository.findByExamType("TYT")).thenReturn(List.of(rule));

        List<ExamTypeSubjectRuleResponse> responses = examRuleService.listRules("TYT");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getSubjectName()).isEqualTo("Matematik");
        assertThat(responses.get(0).getQuestionCount()).isEqualTo(40);
    }
}
