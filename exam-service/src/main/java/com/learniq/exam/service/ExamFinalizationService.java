package com.learniq.exam.service;

import com.learniq.common.tenant.TenantContext;
import com.learniq.exam.domain.Exam;
import com.learniq.exam.domain.StudentExamResult;
import com.learniq.exam.exception.EntityNotFoundException;
import com.learniq.exam.repository.ExamRepository;
import com.learniq.exam.repository.StudentExamResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamFinalizationService {

    private final ExamRepository examRepository;
    private final StudentExamResultRepository studentExamResultRepository;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public void finalizeExam(UUID examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new EntityNotFoundException("Exam", examId));

        if (exam.getStatus() == Exam.ExamStatus.FINALIZED) {
            throw new IllegalStateException("Sınav zaten sonlandırılmış.");
        }

        exam.setStatus(Exam.ExamStatus.FINALIZED);
        exam.setFinalizedAt(LocalDateTime.now());
        examRepository.save(exam);

        // Fetch all approved results for this exam, ordered by score descending
        List<StudentExamResult> approvedResults = studentExamResultRepository
                .findByExamIdAndStatusOrderByTotalScoreDesc(examId, StudentExamResult.ResultStatus.APPROVED);

        if (approvedResults.isEmpty()) {
            log.info("Sonlandırılan sınavda (ID: {}) onaylı sonuç bulunamadı.", examId);
            publishFinalizedEvent(examId);
            return;
        }

        // 1. School Ranking (Overall)
        applyRanking(approvedResults, true);

        // 2. Class Ranking & Percentile
        // Fetch student -> classroom mapping
        Set<UUID> studentIds = approvedResults.stream().map(StudentExamResult::getStudentId).collect(Collectors.toSet());
        Map<UUID, UUID> studentClassroomMap = fetchStudentClassrooms(studentIds);

        // Group by classroom
        Map<UUID, List<StudentExamResult>> classGroups = new HashMap<>();
        for (StudentExamResult result : approvedResults) {
            UUID classroomId = studentClassroomMap.get(result.getStudentId());
            if (classroomId != null) {
                classGroups.computeIfAbsent(classroomId, k -> new ArrayList<>()).add(result);
            }
        }

        // Calculate ranks within each class
        for (List<StudentExamResult> classResults : classGroups.values()) {
            applyRanking(classResults, false);
            calculatePercentiles(classResults);
        }

        studentExamResultRepository.saveAll(approvedResults);

        publishFinalizedEvent(examId);
    }

    private void applyRanking(List<StudentExamResult> results, boolean isSchoolRank) {
        int rank = 1;
        int displayRank = 1;
        BigDecimal prevScore = null;

        for (StudentExamResult r : results) {
            if (prevScore != null && r.getTotalScore().compareTo(prevScore) == 0) {
                // Eşit puan durumu (Tie)
                if (isSchoolRank) r.setSchoolRank(displayRank);
                else r.setClassRank(displayRank);
            } else {
                displayRank = rank;
                if (isSchoolRank) r.setSchoolRank(displayRank);
                else r.setClassRank(displayRank);
            }
            prevScore = r.getTotalScore();
            rank++;
        }
    }

    private void calculatePercentiles(List<StudentExamResult> classResults) {
        int totalStudents = classResults.size();
        if (totalStudents == 0) return;

        for (StudentExamResult r : classResults) {
            // Yüzdelik Dilim = (Sıra / Toplam Öğrenci) * 100
            // Not: İlk sıradaki en düşük yüzdelik dilime sahip olur (örneğin %1'lik dilim).
            BigDecimal percentile = BigDecimal.valueOf(r.getClassRank() * 100.0 / totalStudents)
                    .setScale(2, RoundingMode.HALF_UP);
            r.setPercentile(percentile);
        }
    }

    private Map<UUID, UUID> fetchStudentClassrooms(Set<UUID> studentIds) {
        if (studentIds.isEmpty()) return Collections.emptyMap();
        
        String sql = "SELECT student_id, classroom_id FROM classroom_students WHERE student_id IN (:ids)";
        MapSqlParameterSource params = new MapSqlParameterSource("ids", studentIds);
        
        Map<UUID, UUID> map = new HashMap<>();
        namedParameterJdbcTemplate.query(sql, params, rs -> {
            map.put(UUID.fromString(rs.getString("student_id")),
                    UUID.fromString(rs.getString("classroom_id")));
        });
        return map;
    }

    private void publishFinalizedEvent(UUID examId) {
        // AI ve diğer asenkron işler için olay fırlatılır.
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "EXAM_FINALIZED");
        event.put("tenantId", TenantContext.getCurrentTenant());
        event.put("examId", examId.toString());
        
        com.learniq.exam.messaging.KafkaAfterCommit.send(kafkaTemplate, "exam-results-topic", event);
        log.info("ExamFinalizedEvent commit sonrası gönderilmek üzere kuyruğa alındı. ExamID: {}", examId);
    }
}
