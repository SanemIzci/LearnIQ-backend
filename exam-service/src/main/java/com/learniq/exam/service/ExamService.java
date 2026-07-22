package com.learniq.exam.service;

import com.learniq.exam.domain.Exam;
import com.learniq.exam.domain.Subject;
import com.learniq.exam.dto.ExamCreateRequest;
import com.learniq.exam.dto.ExamResponse;
import com.learniq.exam.dto.ExamUpdateRequest;
import com.learniq.exam.dto.SubjectResponse;
import com.learniq.exam.exception.EntityNotFoundException;
import com.learniq.exam.repository.ExamRepository;
import com.learniq.exam.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRepository examRepository;
    private final SubjectRepository subjectRepository;

    // ── Endpoint #1: Create Exam ───────────────────────────────────────────────

    @Transactional
    public ExamResponse createExam(ExamCreateRequest request) {
        Exam exam = Exam.builder()
                .title(request.getTitle())
                .examDate(request.getExamDate())
                .examType(request.getExamType())
                .build();

        Exam saved = examRepository.save(exam);
        log.info("Created exam '{}' (type={}) with id={}", saved.getTitle(), saved.getExamType(), saved.getId());
        return toResponse(saved);
    }

    // ── Endpoint #2: List All Exams ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ExamResponse> listExams() {
        return examRepository.findAllWithSubjectsOrderByDateDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Endpoint #3: Get Single Exam ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public ExamResponse getExamById(UUID examId) {
        Exam exam = examRepository.findByIdWithSubjects(examId)
                .orElseThrow(() -> new EntityNotFoundException("Exam", examId));
        return toResponse(exam);
    }

    // ── Endpoint #4: Update Exam (Partial / PATCH semantics) ───────────────────

    @Transactional
    public ExamResponse updateExam(UUID examId, ExamUpdateRequest request) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new EntityNotFoundException("Exam", examId));

        // Apply only non-null fields from request
        if (request.getTitle() != null) {
            exam.setTitle(request.getTitle());
        }
        if (request.getExamDate() != null) {
            exam.setExamDate(request.getExamDate());
        }
        if (request.getExamType() != null) {
            exam.setExamType(request.getExamType());
        }

        Exam updated = examRepository.save(exam);
        log.info("Updated exam id={}", updated.getId());
        return toResponse(updated);
    }

    // ── Endpoint #5: Delete Exam ───────────────────────────────────────────────

    @Transactional
    public void deleteExam(UUID examId) {
        if (!examRepository.existsById(examId)) {
            throw new EntityNotFoundException("Exam", examId);
        }
        examRepository.deleteById(examId);
        log.info("Deleted exam id={}", examId);
    }

    // ── Endpoint #8: Add Subject to Exam ──────────────────────────────────────

    @Transactional
    public ExamResponse addSubjectToExam(UUID examId, UUID subjectId) {
        // Use findByIdWithSubjects to get the LAZY subjects collection loaded
        Exam exam = examRepository.findByIdWithSubjects(examId)
                .orElseThrow(() -> new EntityNotFoundException("Exam", examId));

        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new EntityNotFoundException("Subject", subjectId));

        // The Set<Subject> prevents duplicates without extra queries
        exam.getSubjects().add(subject);
        Exam updated = examRepository.save(exam);

        log.info("Mapped subject '{}' to exam '{}'", subject.getName(), exam.getTitle());
        return toResponse(updated);
    }

    // ── Endpoint #9: Remove Subject from Exam ─────────────────────────────────

    @Transactional
    public ExamResponse removeSubjectFromExam(UUID examId, UUID subjectId) {
        Exam exam = examRepository.findByIdWithSubjects(examId)
                .orElseThrow(() -> new EntityNotFoundException("Exam", examId));

        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new EntityNotFoundException("Subject", subjectId));

        exam.getSubjects().remove(subject);
        Exam updated = examRepository.save(exam);

        log.info("Removed subject '{}' from exam '{}'", subject.getName(), exam.getTitle());
        return toResponse(updated);
    }

    // ── Mapping helper ─────────────────────────────────────────────────────────

    private ExamResponse toResponse(Exam exam) {
        List<SubjectResponse> subjectResponses = exam.getSubjects().stream()
                .map(s -> SubjectResponse.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .createdAt(s.getCreatedAt())
                        .build())
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList();

        return ExamResponse.builder()
                .id(exam.getId())
                .title(exam.getTitle())
                .examDate(exam.getExamDate())
                .examType(exam.getExamType())
                .subjects(subjectResponses)
                .createdAt(exam.getCreatedAt())
                .build();
    }
}
