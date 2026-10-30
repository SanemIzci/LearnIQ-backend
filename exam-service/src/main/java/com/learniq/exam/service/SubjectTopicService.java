package com.learniq.exam.service;

import com.learniq.exam.domain.Subject;
import com.learniq.exam.domain.SubjectTopic;
import com.learniq.exam.dto.CreateSubjectTopicRequest;
import com.learniq.exam.dto.SubjectTopicResponse;
import com.learniq.exam.dto.UpdateSubjectTopicRequest;
import com.learniq.exam.exception.EntityNotFoundException;
import com.learniq.exam.repository.SubjectRepository;
import com.learniq.exam.repository.SubjectTopicRepository;
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
public class SubjectTopicService {

    private final SubjectRepository subjectRepository;
    private final SubjectTopicRepository subjectTopicRepository;

    /**
     * Lists all topics for a specific subject, ordered by order_index and name.
     */
    @Transactional(readOnly = true)
    public List<SubjectTopicResponse> listTopicsBySubject(UUID subjectId) {
        if (!subjectRepository.existsById(subjectId)) {
            throw new EntityNotFoundException("Subject", subjectId);
        }

        return subjectTopicRepository.findBySubjectIdOrderByOrderIndexAscNameAsc(subjectId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Retrieves a single topic by subject ID and topic ID.
     */
    @Transactional(readOnly = true)
    public SubjectTopicResponse getTopic(UUID subjectId, UUID topicId) {
        SubjectTopic topic = subjectTopicRepository.findByIdAndSubjectId(topicId, subjectId)
                .orElseThrow(() -> new EntityNotFoundException("SubjectTopic", topicId));
        return toResponse(topic);
    }

    /**
     * Creates a new topic for a given subject.
     * Guards against duplicate names within the same subject.
     */
    @Transactional
    public SubjectTopicResponse createTopic(UUID subjectId, CreateSubjectTopicRequest request) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new EntityNotFoundException("Subject", subjectId));

        String trimmedName = request.getName().trim();
        if (subjectTopicRepository.existsBySubjectIdAndNameIgnoreCase(subjectId, trimmedName)) {
            throw new DataIntegrityViolationException(
                    "Topic with name '" + trimmedName + "' already exists for this subject.");
        }

        SubjectTopic topic = SubjectTopic.builder()
                .subject(subject)
                .name(trimmedName)
                .code(request.getCode() != null ? request.getCode().trim() : null)
                .orderIndex(request.getOrderIndex() != null ? request.getOrderIndex() : 0)
                .build();

        SubjectTopic saved = subjectTopicRepository.save(topic);
        log.info("Created topic '{}' (id={}) for subject '{}' (id={})",
                saved.getName(), saved.getId(), subject.getName(), subject.getId());

        return toResponse(saved);
    }

    /**
     * Updates an existing topic.
     */
    @Transactional
    public SubjectTopicResponse updateTopic(UUID subjectId, UUID topicId, UpdateSubjectTopicRequest request) {
        SubjectTopic topic = subjectTopicRepository.findByIdAndSubjectId(topicId, subjectId)
                .orElseThrow(() -> new EntityNotFoundException("SubjectTopic", topicId));

        String trimmedName = request.getName().trim();
        if (subjectTopicRepository.existsBySubjectIdAndNameIgnoreCaseAndIdNot(subjectId, trimmedName, topicId)) {
            throw new DataIntegrityViolationException(
                    "Topic with name '" + trimmedName + "' already exists for this subject.");
        }

        topic.setName(trimmedName);
        topic.setCode(request.getCode() != null ? request.getCode().trim() : null);
        if (request.getOrderIndex() != null) {
            topic.setOrderIndex(request.getOrderIndex());
        }

        SubjectTopic updated = subjectTopicRepository.save(topic);
        log.info("Updated topic id={} for subject id={}", updated.getId(), subjectId);

        return toResponse(updated);
    }

    /**
     * Deletes a topic.
     */
    @Transactional
    public void deleteTopic(UUID subjectId, UUID topicId) {
        SubjectTopic topic = subjectTopicRepository.findByIdAndSubjectId(topicId, subjectId)
                .orElseThrow(() -> new EntityNotFoundException("SubjectTopic", topicId));

        subjectTopicRepository.delete(topic);
        log.info("Deleted topic id={} for subject id={}", topicId, subjectId);
    }

    // ── Mapping ────────────────────────────────────────────────────────────────

    private SubjectTopicResponse toResponse(SubjectTopic topic) {
        return SubjectTopicResponse.builder()
                .id(topic.getId())
                .subjectId(topic.getSubject().getId())
                .name(topic.getName())
                .code(topic.getCode())
                .orderIndex(topic.getOrderIndex())
                .createdAt(topic.getCreatedAt())
                .build();
    }
}
