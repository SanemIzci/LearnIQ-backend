package com.learniq.exam.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learniq.exam.domain.TeacherObservation;
import com.learniq.exam.dto.CreateObservationRequest;
import com.learniq.exam.dto.ObservationResponse;
import com.learniq.exam.dto.UpdateObservationRequest;
import com.learniq.exam.exception.EntityNotFoundException;
import com.learniq.exam.messaging.KafkaAfterCommit;
import com.learniq.exam.repository.TeacherObservationRepository;
import com.learniq.exam.security.AccessControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherObservationService {

    private final TeacherObservationRepository observationRepository;
    private final AccessControlService accessControlService;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public static final String OBSERVATION_TOPIC = "teacher-observations-topic";

    // ── Create ───────────────────────────────────────────────────────────────

    @Transactional
    public ObservationResponse create(
            String tenantId,
            UUID teacherId,
            String role,
            UUID classroomId,
            CreateObservationRequest request) {

        accessControlService.validateClassroomAccess(tenantId, teacherId, role, classroomId);

        TeacherObservation observation = TeacherObservation.builder()
                .teacherId(teacherId)
                .classroomId(classroomId)
                .subjectId(request.getSubjectId())
                .examId(request.getExamId())
                .content(request.getContent().trim())
                .tags(serializeTags(request.getTags()))
                .build();

        TeacherObservation saved = observationRepository.save(observation);
        publishObservationEvent(tenantId, "TEACHER_OBSERVATION_CREATED", saved.getId(), classroomId);
        log.info("Gözlem notu oluşturuldu: id={} teacher={} classroom={}", saved.getId(), teacherId, classroomId);
        return toResponse(saved);
    }

    // ── List ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ObservationResponse> listByClassroom(
            String tenantId,
            UUID teacherId,
            String role,
            UUID classroomId) {

        accessControlService.validateClassroomAccess(tenantId, teacherId, role, classroomId);

        return observationRepository
                .findByTeacherIdAndClassroomIdOrderByCreatedAtDesc(teacherId, classroomId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ObservationResponse> listAll(UUID teacherId) {
        return observationRepository
                .findByTeacherIdOrderByCreatedAtDesc(teacherId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Get ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ObservationResponse getById(UUID teacherId, UUID observationId) {
        TeacherObservation observation = findOwnedOrThrow(teacherId, observationId);
        return toResponse(observation);
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @Transactional
    public ObservationResponse update(UUID teacherId, UUID observationId, UpdateObservationRequest request) {
        TeacherObservation observation = findOwnedOrThrow(teacherId, observationId);

        if (request.getContent() != null && !request.getContent().isBlank()) {
            observation.setContent(request.getContent().trim());
        }
        if (request.getSubjectId() != null) {
            observation.setSubjectId(request.getSubjectId());
        }
        if (request.getExamId() != null) {
            observation.setExamId(request.getExamId());
        }
        if (request.getTags() != null) {
            observation.setTags(serializeTags(request.getTags()));
        }

        TeacherObservation saved = observationRepository.save(observation);
        String tenantSchema = com.learniq.common.tenant.TenantContext.getCurrentTenant();
        publishObservationEvent(tenantSchema, "TEACHER_OBSERVATION_UPDATED", observationId, saved.getClassroomId());
        log.info("Gözlem notu güncellendi: id={}", observationId);
        return toResponse(saved);
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID teacherId, UUID observationId) {
        TeacherObservation observation = findOwnedOrThrow(teacherId, observationId);
        String tenantSchema = com.learniq.common.tenant.TenantContext.getCurrentTenant();
        observationRepository.delete(observation);
        publishObservationEvent(tenantSchema, "TEACHER_OBSERVATION_DELETED", observationId, observation.getClassroomId());
        log.info("Gözlem notu silindi: id={}", observationId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void publishObservationEvent(String tenantSchema, String eventType, UUID observationId, UUID classroomId) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("tenantId", tenantSchema);
        event.put("observationId", observationId.toString());
        if (classroomId != null) {
            event.put("classroomId", classroomId.toString());
        }
        // regex doğrulama(validasyon) yayından önce: kötü şema adı ile event üretme
        com.learniq.common.tenant.TenantSchemaValidator.requireValid(tenantSchema);
        KafkaAfterCommit.send(kafkaTemplate, OBSERVATION_TOPIC, event);
        log.info("Gözlem olayı kuyruğa alındı: {} id={}", eventType, observationId);
    }

    private TeacherObservation findOwnedOrThrow(UUID teacherId, UUID observationId) {
        return observationRepository.findByIdAndTeacherId(observationId, teacherId)
                .orElseThrow(() -> new EntityNotFoundException("TeacherObservation", observationId));
    }

    private String serializeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException e) {
            log.warn("Tags serialize edilemedi, null olarak kaydediliyor", e);
            return null;
        }
    }

    private List<String> deserializeTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Tags deserialize edilemedi: {}", tagsJson, e);
            return Collections.emptyList();
        }
    }

    ObservationResponse toResponse(TeacherObservation obs) {
        return ObservationResponse.builder()
                .id(obs.getId())
                .teacherId(obs.getTeacherId())
                .classroomId(obs.getClassroomId())
                .subjectId(obs.getSubjectId())
                .examId(obs.getExamId())
                .content(obs.getContent())
                .tags(deserializeTags(obs.getTags()))
                .createdAt(obs.getCreatedAt())
                .updatedAt(obs.getUpdatedAt())
                .build();
    }
}
