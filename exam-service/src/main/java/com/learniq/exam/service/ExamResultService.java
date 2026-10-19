package com.learniq.exam.service;

import com.learniq.exam.domain.Exam;
import com.learniq.exam.domain.StudentExamResult;
import com.learniq.exam.domain.StudentSubjectResult;
import com.learniq.exam.domain.Subject;
import com.learniq.exam.dto.ExamResultSubmitRequest;
import com.learniq.exam.dto.SubjectInputDto;
import com.learniq.exam.exception.EntityNotFoundException;
import com.learniq.exam.repository.ExamRepository;
import com.learniq.exam.repository.StudentExamResultRepository;
import com.learniq.exam.repository.StudentSubjectResultRepository;
import com.learniq.exam.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExamResultService {

    private final ExamRepository examRepository;
    private final SubjectRepository subjectRepository;
    private final StudentExamResultRepository studentExamResultRepository;
    private final StudentSubjectResultRepository studentSubjectResultRepository;
    private final NetCalculator netCalculator;

    @Transactional
    public void submitResult(UUID examId, UUID studentId, ExamResultSubmitRequest request) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new EntityNotFoundException("Exam", examId));

        if (exam.getStatus() != Exam.ExamStatus.OPEN) {
            throw new IllegalStateException("Sınav kapalı durumda, sonuç girilemez.");
        }

        // Varsa eski reddedilmiş sonucu temizle (Öğrenci düzeltip tekrar gönderiyor olabilir)
        studentExamResultRepository.findByExamIdAndStudentId(examId, studentId)
                .ifPresent(existing -> {
                    if (existing.getStatus() == StudentExamResult.ResultStatus.APPROVED) {
                        throw new IllegalStateException("Bu sınav için onaylanmış bir sonucunuz var.");
                    }
                    studentSubjectResultRepository.deleteByExamIdAndStudentId(examId, studentId);
                    studentExamResultRepository.delete(existing);
                    studentExamResultRepository.flush();
                });

        BigDecimal totalScore = BigDecimal.ZERO;

        for (SubjectInputDto subjectDto : request.getSubjects()) {
            Subject subject = subjectRepository.findById(subjectDto.getSubjectId())
                    .orElseThrow(() -> new EntityNotFoundException("Subject", subjectDto.getSubjectId()));

            BigDecimal netScore = netCalculator.calculateNetScore(
                    exam.getExamType(), subject.getId(), subjectDto.getCorrectCount(), subjectDto.getWrongCount());

            totalScore = totalScore.add(netScore);

            StudentSubjectResult subjectResult = StudentSubjectResult.builder()
                    .studentId(studentId)
                    .exam(exam)
                    .subject(subject)
                    .correctCount(subjectDto.getCorrectCount())
                    .wrongCount(subjectDto.getWrongCount())
                    .netScore(netScore)
                    .build();

            studentSubjectResultRepository.save(subjectResult);
        }

        StudentExamResult examResult = StudentExamResult.builder()
                .studentId(studentId)
                .exam(exam)
                .totalScore(totalScore)
                .status(StudentExamResult.ResultStatus.SUBMITTED)
                .entryMethod(StudentExamResult.EntryMethod.MANUAL)
                .build();

        studentExamResultRepository.save(examResult);
    }

    @Transactional
    public void approveResult(UUID examId, UUID resultId, UUID teacherId) {
        StudentExamResult result = studentExamResultRepository.findById(resultId)
                .orElseThrow(() -> new EntityNotFoundException("StudentExamResult", resultId));

        if (!result.getExam().getId().equals(examId)) {
            throw new IllegalArgumentException("Sonuç bu sınava ait değil.");
        }

        result.setStatus(StudentExamResult.ResultStatus.APPROVED);
        result.setReviewedBy(teacherId);
        result.setReviewedAt(LocalDateTime.now());
        result.setRejectionReason(null);
        studentExamResultRepository.save(result);
    }

    @Transactional
    public void rejectResult(UUID examId, UUID resultId, UUID teacherId, String reason) {
        StudentExamResult result = studentExamResultRepository.findById(resultId)
                .orElseThrow(() -> new EntityNotFoundException("StudentExamResult", resultId));

        if (!result.getExam().getId().equals(examId)) {
            throw new IllegalArgumentException("Sonuç bu sınava ait değil.");
        }

        result.setStatus(StudentExamResult.ResultStatus.REJECTED);
        result.setReviewedBy(teacherId);
        result.setReviewedAt(LocalDateTime.now());
        result.setRejectionReason(reason);
        studentExamResultRepository.save(result);
    }
}
