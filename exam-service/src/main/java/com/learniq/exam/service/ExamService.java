package com.learniq.exam.service;

import com.learniq.common.tenant.TenantContext;
import com.learniq.common.tenant.TenantSchemaValidator;
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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRepository examRepository;
    private final SubjectRepository subjectRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final JdbcTemplate jdbcTemplate;

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
        String tenantSchema = com.learniq.common.tenant.TenantContext.getCurrentTenant();
        examRepository.deleteById(examId);

        // RAG reconciliation: sınav silinince türetilen fact dokümanları tombstone'lanır.
        // DELETE aggregate'i terminal olduğu için version = duvar saati (monotonik üstünlük).
        if (tenantSchema != null && !tenantSchema.isBlank()) {
            com.learniq.common.tenant.TenantSchemaValidator.requireValid(tenantSchema);
            Map<String, Object> payload = new HashMap<>();
            payload.put("examId", examId.toString());
            Map<String, Object> event = com.learniq.common.messaging.RagEvent.envelope(
                    "EXAM_DELETED", tenantSchema, "exam:" + examId,
                    System.currentTimeMillis(), payload);
            com.learniq.exam.messaging.KafkaAfterCommit.send(
                    kafkaTemplate, "class-analysis-topic", "exam:" + examId, event);
        }
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

    // ── Classroom assignments ────────────────────────────────────────────────

    private String resolveSchema(String tenantId) {
        if (tenantId != null && !tenantId.isBlank()) {
            return TenantSchemaValidator.requireValid(tenantId);
        }
        return TenantSchemaValidator.requireValid(TenantContext.getCurrentTenant());
    }

    @Transactional
    public ExamResponse assignClassroom(String tenantId, UUID examId, UUID classroomId) {
        String schema = resolveSchema(tenantId);

        // 1. Sınavın kiracıda var olduğunu doğrula
        Exam exam = examRepository.findByIdWithSubjects(examId)
                .orElseThrow(() -> new EntityNotFoundException("Exam", examId));

        // 2. Sınıfın aynı kiracıda var olduğunu doğrula
        List<Integer> classroomRows = jdbcTemplate.queryForList(
                "SELECT 1 FROM %s.classrooms WHERE id = ?".formatted(schema),
                Integer.class, classroomId);
        if (classroomRows.isEmpty()) {
            throw new EntityNotFoundException("Classroom", classroomId);
        }

        // 3. Duplicate assignment engelle (409 Conflict)
        if (exam.getClassroomIds() != null && exam.getClassroomIds().contains(classroomId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Exam is already assigned to this classroom");
        }

        List<Integer> existing = jdbcTemplate.queryForList(
                "SELECT 1 FROM %s.exam_classrooms WHERE exam_id = ? AND classroom_id = ?".formatted(schema),
                Integer.class, examId, classroomId);
        if (!existing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Exam is already assigned to this classroom");
        }

        // 4. İlişkiyi kaydet
        if (exam.getClassroomIds() != null) {
            exam.getClassroomIds().add(classroomId);
        }
        Exam updated = examRepository.save(exam);

        log.info("Assigned exam {} to classroom {} in tenant {}", examId, classroomId, tenantId);
        return toResponse(updated);
    }

    @Transactional
    public ExamResponse unassignClassroom(String tenantId, UUID examId, UUID classroomId) {
        String schema = resolveSchema(tenantId);

        // 1. Sınavın kiracıda var olduğunu doğrula
        Exam exam = examRepository.findByIdWithSubjects(examId)
                .orElseThrow(() -> new EntityNotFoundException("Exam", examId));

        // 2. Sınıfın aynı kiracıda var olduğunu doğrula
        List<Integer> classroomRows = jdbcTemplate.queryForList(
                "SELECT 1 FROM %s.classrooms WHERE id = ?".formatted(schema),
                Integer.class, classroomId);
        if (classroomRows.isEmpty()) {
            throw new EntityNotFoundException("Classroom", classroomId);
        }

        // 3. Eşleşmenin var olduğunu doğrula (yoksa 404)
        boolean inSet = exam.getClassroomIds() != null && exam.getClassroomIds().contains(classroomId);
        List<Integer> existing = jdbcTemplate.queryForList(
                "SELECT 1 FROM %s.exam_classrooms WHERE exam_id = ? AND classroom_id = ?".formatted(schema),
                Integer.class, examId, classroomId);

        if (!inSet && existing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Exam is not assigned to this classroom");
        }

        // 4. İlişkiyi kaldır (öğrencinin geçmiş sınav sonuçları student_exam_results korunur)
        if (exam.getClassroomIds() != null) {
            exam.getClassroomIds().remove(classroomId);
        }
        Exam updated = examRepository.save(exam);

        log.info("Unassigned exam {} from classroom {} in tenant {}", examId, classroomId, tenantId);
        return toResponse(updated);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listClassroomsForExam(String tenantId, UUID examId) {
        String schema = resolveSchema(tenantId);

        if (!examRepository.existsById(examId)) {
            throw new EntityNotFoundException("Exam", examId);
        }

        return jdbcTemplate.queryForList(
                """
                SELECT c.id, c.name
                FROM %1$s.classrooms c
                JOIN %1$s.exam_classrooms ec ON ec.classroom_id = c.id
                WHERE ec.exam_id = ?
                ORDER BY c.name
                """.formatted(schema),
                examId);
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

        List<UUID> classroomIdList = exam.getClassroomIds() != null
                ? exam.getClassroomIds().stream().sorted().toList()
                : List.of();

        return ExamResponse.builder()
                .id(exam.getId())
                .title(exam.getTitle())
                .examDate(exam.getExamDate())
                .examType(exam.getExamType())
                .subjects(subjectResponses)
                .classroomIds(classroomIdList)
                .createdAt(exam.getCreatedAt())
                .build();
    }
}
