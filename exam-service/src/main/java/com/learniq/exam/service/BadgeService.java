package com.learniq.exam.service;

import com.learniq.exam.domain.Badge;
import com.learniq.exam.domain.Exam;
import com.learniq.exam.domain.StudentBadge;
import com.learniq.exam.domain.StudentExamResult;
import com.learniq.exam.repository.BadgeRepository;
import com.learniq.exam.repository.ExamRepository;
import com.learniq.exam.repository.StudentBadgeRepository;
import com.learniq.exam.repository.StudentExamResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BadgeService {

    private final StudentExamResultRepository studentExamResultRepository;
    private final StudentBadgeRepository studentBadgeRepository;
    private final BadgeRepository badgeRepository;
    private final ExamRepository examRepository;

    private static final String BADGE_TOP_3 = "TOP_3";
    private static final String BADGE_FIRST_EXAM = "FIRST_EXAM";

    @Transactional
    public void evaluateBadgesForExam(UUID examId) {
        log.info("Rozet değerlendirmesi başladı. Sınav ID: {}", examId);

        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalArgumentException("Sınav bulunamadı"));

        if (exam.getStatus() != Exam.ExamStatus.FINALIZED) {
            log.warn("Sınav henüz FINALIZED değil. Rozet verilmeyecek. Sınav ID: {}", examId);
            return;
        }

        List<StudentExamResult> results = studentExamResultRepository.findByExamIdAndStatus(examId, StudentExamResult.ResultStatus.APPROVED);

        // Pre-fetch badges to avoid DB calls in loop
        Badge top3Badge = badgeRepository.findById(BADGE_TOP_3).orElse(null);
        Badge firstExamBadge = badgeRepository.findById(BADGE_FIRST_EXAM).orElse(null);

        for (StudentExamResult result : results) {
            UUID studentId = result.getStudentId();

            // Kural 1: İlk Sınav Rozeti (FIRST_EXAM)
            if (firstExamBadge != null) {
                long finalizedExamCount = studentExamResultRepository.findAll().stream()
                        .filter(r -> r.getStudentId().equals(studentId) 
                                  && r.getExam().getStatus() == Exam.ExamStatus.FINALIZED
                                  && r.getStatus() == StudentExamResult.ResultStatus.APPROVED)
                        .count();
                
                if (finalizedExamCount == 1) { // Sadece bu sınavı var
                    awardBadge(studentId, exam, firstExamBadge);
                }
            }

            // Kural 2: Sınıf İlk 3 Rozeti (TOP_3)
            if (top3Badge != null && result.getClassRank() != null && result.getClassRank() <= 3) {
                awardBadge(studentId, exam, top3Badge);
            }
        }
        
        log.info("Rozet değerlendirmesi tamamlandı. Sınav ID: {}", examId);
    }

    private void awardBadge(UUID studentId, Exam exam, Badge badge) {
        // Zaten aynı sınav için bu rozeti almış mı kontrol et
        boolean exists = studentBadgeRepository.existsByStudentIdAndBadgeCodeAndExamId(
                studentId, badge.getCode(), exam.getId());
        
        if (!exists) {
            StudentBadge sb = StudentBadge.builder()
                    .studentId(studentId)
                    .badge(badge)
                    .exam(exam)
                    .build();
            studentBadgeRepository.save(sb);
            log.debug("Öğrenciye (ID: {}) {} rozeti verildi.", studentId, badge.getTitle());
        }
    }
}
