package com.learniq.exam.service;

import com.learniq.exam.domain.Badge;
import com.learniq.exam.domain.Exam;
import com.learniq.exam.domain.StudentBadge;
import com.learniq.exam.domain.StudentExamResult;
import com.learniq.exam.repository.BadgeRepository;
import com.learniq.exam.repository.ExamRepository;
import com.learniq.exam.repository.StudentBadgeRepository;
import com.learniq.exam.repository.StudentExamResultRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BadgeServiceTest {

    @Mock private StudentExamResultRepository examResultRepository;
    @Mock private StudentBadgeRepository studentBadgeRepository;
    @Mock private BadgeRepository badgeRepository;
    @Mock private ExamRepository examRepository;

    @InjectMocks
    private BadgeService badgeService;

    private Exam finalizedExam(UUID examId) {
        Exam exam = new Exam();
        exam.setId(examId);
        exam.setStatus(Exam.ExamStatus.FINALIZED);
        return exam;
    }

    private StudentExamResult approved(UUID examId, UUID studentId, Integer classRank) {
        StudentExamResult r = new StudentExamResult();
        r.setId(UUID.randomUUID());
        r.setStudentId(studentId);
        r.setExam(finalizedExam(examId));
        r.setStatus(StudentExamResult.ResultStatus.APPROVED);
        r.setClassRank(classRank);
        return r;
    }

    private Badge badge(String code, String title) {
        return new Badge(code, title, "açıklama");
    }

    @Test
    @DisplayName("Sınıf ilk 3'üne giren öğrenci TOP_3 rozeti alır, 4. sıradaki almaz")
    void evaluateBadgesForExam_top3_onlyAwardsTopThree() {
        UUID examId = UUID.randomUUID();
        Exam exam = finalizedExam(examId);

        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));

        StudentExamResult first = approved(examId, UUID.randomUUID(), 1);
        StudentExamResult fourth = approved(examId, UUID.randomUUID(), 4);
        when(examResultRepository.findByExamIdAndStatus(examId, StudentExamResult.ResultStatus.APPROVED))
                .thenReturn(List.of(first, fourth));

        when(badgeRepository.findById("TOP_3")).thenReturn(Optional.of(badge("TOP_3", "İlk 3")));
        when(badgeRepository.findById("FIRST_EXAM")).thenReturn(Optional.empty());
        when(studentBadgeRepository.existsByStudentIdAndBadgeCodeAndExamId(any(), any(), any())).thenReturn(false);

        badgeService.evaluateBadgesForExam(examId);

        verify(studentBadgeRepository, times(1)).save(any(StudentBadge.class));
    }

    @Test
    @DisplayName("FIRST_EXAM: öğrencinin tek finalized+approved sınavı ise verilir")
    void evaluateBadgesForExam_firstExam_awardedWhenSingleFinalizedExam() {
        UUID examId = UUID.randomUUID();
        Exam exam = finalizedExam(examId);
        UUID studentId = UUID.randomUUID();

        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));

        StudentExamResult result = approved(examId, studentId, 10);
        when(examResultRepository.findByExamIdAndStatus(examId, StudentExamResult.ResultStatus.APPROVED))
                .thenReturn(List.of(result));
        when(examResultRepository.findAll()).thenReturn(List.of(result));

        when(badgeRepository.findById("TOP_3")).thenReturn(Optional.empty());
        when(badgeRepository.findById("FIRST_EXAM")).thenReturn(Optional.of(badge("FIRST_EXAM", "İlk Sınav")));
        when(studentBadgeRepository.existsByStudentIdAndBadgeCodeAndExamId(any(), any(), any())).thenReturn(false);

        badgeService.evaluateBadgesForExam(examId);

        verify(studentBadgeRepository, times(1)).save(any(StudentBadge.class));
    }

    @Test
    @DisplayName("FIRST_EXAM: öğrencinin daha önce başka finalized sınavı varsa verilmez")
    void evaluateBadgesForExam_firstExam_notAwardedWhenPriorFinalizedExamExists() {
        UUID examId = UUID.randomUUID();
        Exam exam = finalizedExam(examId);
        UUID studentId = UUID.randomUUID();

        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));

        StudentExamResult current = approved(examId, studentId, 10);
        StudentExamResult previous = approved(UUID.randomUUID(), studentId, 2); // eski finalized sınav

        when(examResultRepository.findByExamIdAndStatus(examId, StudentExamResult.ResultStatus.APPROVED))
                .thenReturn(List.of(current));
        when(examResultRepository.findAll()).thenReturn(List.of(current, previous));

        when(badgeRepository.findById("TOP_3")).thenReturn(Optional.empty());
        when(badgeRepository.findById("FIRST_EXAM")).thenReturn(Optional.of(badge("FIRST_EXAM", "İlk Sınav")));

        badgeService.evaluateBadgesForExam(examId);

        verify(studentBadgeRepository, never()).save(any(StudentBadge.class));
    }

    @Test
    @DisplayName("Kafka aynı mesajı iki kez getirirse rozet ikinci kez yazılmaz (idempotent)")
    void evaluateBadgesForExam_duplicateKafkaDelivery_isIdempotent() {
        UUID examId = UUID.randomUUID();
        Exam exam = finalizedExam(examId);

        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));

        StudentExamResult result = approved(examId, UUID.randomUUID(), 1);
        when(examResultRepository.findByExamIdAndStatus(examId, StudentExamResult.ResultStatus.APPROVED))
                .thenReturn(List.of(result));

        when(badgeRepository.findById("TOP_3")).thenReturn(Optional.of(badge("TOP_3", "İlk 3")));
        when(badgeRepository.findById("FIRST_EXAM")).thenReturn(Optional.empty());
        // İlk teslimde yok, ikinci teslimde (yeniden dağıtım) artık var
        when(studentBadgeRepository.existsByStudentIdAndBadgeCodeAndExamId(any(), any(), any()))
                .thenReturn(false, true);

        badgeService.evaluateBadgesForExam(examId);
        badgeService.evaluateBadgesForExam(examId);

        verify(studentBadgeRepository, times(1)).save(any(StudentBadge.class));
    }

    @Test
    @DisplayName("Sınav FINALIZED değilse rozet dağıtılmaz")
    void evaluateBadgesForExam_notFinalized_doesNothing() {
        UUID examId = UUID.randomUUID();
        Exam exam = new Exam();
        exam.setId(examId);
        exam.setStatus(Exam.ExamStatus.OPEN);

        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));

        badgeService.evaluateBadgesForExam(examId);

        verify(studentBadgeRepository, never()).save(any(StudentBadge.class));
        verify(badgeRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Sınav bulunamazsa IllegalArgumentException")
    void evaluateBadgesForExam_examNotFound_throws() {
        UUID examId = UUID.randomUUID();
        when(examRepository.findById(examId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> badgeService.evaluateBadgesForExam(examId));
    }

    @Test
    @DisplayName("Onaylı sonuç yoksa hiç rozet yazılmaz")
    void evaluateBadgesForExam_noApprovedResults_awardsNothing() {
        UUID examId = UUID.randomUUID();
        Exam exam = finalizedExam(examId);

        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(examResultRepository.findByExamIdAndStatus(examId, StudentExamResult.ResultStatus.APPROVED))
                .thenReturn(List.of());
        when(badgeRepository.findById("TOP_3")).thenReturn(Optional.of(badge("TOP_3", "İlk 3")));
        when(badgeRepository.findById("FIRST_EXAM")).thenReturn(Optional.of(badge("FIRST_EXAM", "İlk Sınav")));

        badgeService.evaluateBadgesForExam(examId);

        verify(studentBadgeRepository, never()).save(any(StudentBadge.class));
    }

    @Test
    @DisplayName("classRank null ise TOP_3 verilmez")
    void evaluateBadgesForExam_nullClassRank_noTop3() {
        UUID examId = UUID.randomUUID();
        Exam exam = finalizedExam(examId);

        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));

        StudentExamResult result = approved(examId, UUID.randomUUID(), null);
        when(examResultRepository.findByExamIdAndStatus(examId, StudentExamResult.ResultStatus.APPROVED))
                .thenReturn(List.of(result));

        when(badgeRepository.findById("TOP_3")).thenReturn(Optional.of(badge("TOP_3", "İlk 3")));
        when(badgeRepository.findById("FIRST_EXAM")).thenReturn(Optional.empty());

        badgeService.evaluateBadgesForExam(examId);

        verify(studentBadgeRepository, never()).save(any(StudentBadge.class));
    }
}
