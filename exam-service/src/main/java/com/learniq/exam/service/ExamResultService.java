package com.learniq.exam.service;

import com.learniq.common.tenant.TenantContext;
import com.learniq.common.tenant.TenantSchemaValidator;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExamResultService {

    private final ExamRepository examRepository;
    private final SubjectRepository subjectRepository;
    private final StudentExamResultRepository studentExamResultRepository;
    private final StudentSubjectResultRepository studentSubjectResultRepository;
    private final NetCalculator netCalculator;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void submitResult(UUID examId, UUID studentId, ExamResultSubmitRequest request) {
        Exam exam = examRepository.findByIdWithSubjects(examId)
                .orElseThrow(() -> new EntityNotFoundException("Exam", examId));

        if (exam.getStatus() != Exam.ExamStatus.OPEN) {
            throw new IllegalStateException("Sınav kapalı durumda, sonuç girilemez.");
        }

        validateDuplicateSubjects(request);
        validateStudentClassroomAssignment(examId, studentId);

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

            if (!exam.getSubjects().contains(subject)) {
                throw new IllegalArgumentException("Ders bu sınava bağlı değil: " + subject.getName());
            }

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

    private void validateDuplicateSubjects(ExamResultSubmitRequest request) {
        Set<UUID> uniqueSubjectIds = new HashSet<>();
        for (SubjectInputDto subjectDto : request.getSubjects()) {
            if (!uniqueSubjectIds.add(subjectDto.getSubjectId())) {
                throw new IllegalArgumentException("Aynı ders birden fazla kez gönderilemez: " + subjectDto.getSubjectId());
            }
        }
    }

    /**
     * Sınava sınıflar atandıysa öğrenci, bu sınıflardan birine kayıtlı olmalıdır.
     * Atama yoksa (sonuç-giriş akışı, Henüz atama yapılmamış) öğrenciye izin verilir.
     */
    private void validateStudentClassroomAssignment(UUID examId, UUID studentId) {
        String schema = TenantSchemaValidator.requireValid(TenantContext.getCurrentTenant());

        Integer classroomCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM %s.exam_classrooms WHERE exam_id = ?".formatted(schema),
                Integer.class, examId);

        if (classroomCount == null || classroomCount == 0) {
            return;
        }

        Integer enrolled = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM %s.exam_classrooms ec
                JOIN %s.classroom_students cs ON cs.classroom_id = ec.classroom_id
                WHERE ec.exam_id = ? AND cs.student_id = ?
                """.formatted(schema, schema),
                Integer.class, examId, studentId);

        if (enrolled == null || enrolled == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bu sınav sınıfınıza atanmamış, sonuç gönderemezsiniz.");
        }
    }

    @Transactional
    public void approveResult(UUID examId, UUID resultId, UUID teacherId) {
        StudentExamResult result = studentExamResultRepository.findById(resultId)
                .orElseThrow(() -> new EntityNotFoundException("StudentExamResult", resultId));

        if (!result.getExam().getId().equals(examId)) {
            throw new IllegalArgumentException("Sonuç bu sınava ait değil.");
        }

        if (result.getExam().getStatus() == Exam.ExamStatus.FINALIZED) {
            throw new IllegalStateException("Sonlandırılmış bir sınavın sonuçları onaylanamaz.");
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

        if (result.getExam().getStatus() == Exam.ExamStatus.FINALIZED) {
            throw new IllegalStateException("Sonlandırılmış bir sınavın sonuçları reddedilemez.");
        }

        result.setStatus(StudentExamResult.ResultStatus.REJECTED);
        result.setReviewedBy(teacherId);
        result.setReviewedAt(LocalDateTime.now());
        result.setRejectionReason(reason);
        studentExamResultRepository.save(result);
    }
}
