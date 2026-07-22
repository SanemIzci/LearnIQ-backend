package com.learniq.exam.service;

import com.learniq.exam.domain.Subject;
import com.learniq.exam.dto.SubjectRequest;
import com.learniq.exam.dto.SubjectResponse;
import com.learniq.exam.exception.EntityNotFoundException;
import com.learniq.exam.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectRepository subjectRepository;

    /**
     * Creates a new subject in the current tenant's schema.
     * Guards against duplicate names with an explicit pre-check to produce a
     * meaningful 409 Conflict rather than a cryptic DB constraint error.
     */
    @Transactional
    public SubjectResponse createSubject(SubjectRequest request) {
        if (subjectRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DataIntegrityViolationException(
                    "Subject with name '" + request.getName() + "' already exists in this tenant.");
        }

        Subject subject = Subject.builder()
                .name(request.getName())
                .build();

        Subject saved = subjectRepository.save(subject);
        log.info("Created subject '{}' with id={}", saved.getName(), saved.getId());

        return toResponse(saved);
    }

    /**
     * Returns all subjects for the current tenant, sorted alphabetically.
     * No pagination needed until subject counts exceed hundreds per tenant.
     */
    @Transactional(readOnly = true)
    public List<SubjectResponse> listSubjects() {
        return subjectRepository.findAll()
                .stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(this::toResponse)
                .toList();
    }

    /**
     * Looks up a single subject by ID. Used internally by other services (e.g., ExamService).
     */
    @Transactional(readOnly = true)
    public SubjectResponse getSubjectById(UUID subjectId) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new EntityNotFoundException("Subject", subjectId));
        return toResponse(subject);
    }

    // ── Mapping ────────────────────────────────────────────────────────────────

    private SubjectResponse toResponse(Subject subject) {
        return SubjectResponse.builder()
                .id(subject.getId())
                .name(subject.getName())
                .createdAt(subject.getCreatedAt())
                .build();
    }
}
