package com.learniq.exam.service;

import com.learniq.exam.domain.Exam;
import com.learniq.exam.domain.StudentExamResult;
import com.learniq.exam.exception.EntityNotFoundException;
import com.learniq.exam.messaging.ExamFinalizedBadgeConsumer;
import com.learniq.exam.repository.ExamRepository;
import com.learniq.exam.repository.StudentExamResultRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamFinalizationServiceTest {

    @Mock
    private ExamRepository examRepository;

    @Mock
    private StudentExamResultRepository studentExamResultRepository;

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private ExamFinalizationService examFinalizationService;

    private Exam openExam(UUID examId) {
        Exam exam = new Exam();
        exam.setId(examId);
        exam.setStatus(Exam.ExamStatus.OPEN);
        return exam;
    }

    private StudentExamResult approved(UUID studentId, String score) {
        return StudentExamResult.builder()
                .studentId(studentId)
                .totalScore(new BigDecimal(score))
                .status(StudentExamResult.ResultStatus.APPROVED)
                .build();
    }

    /**
     * fetchStudentClassrooms sorgusunun ResultSet'ini taklit eder.
     * map: studentId -> classroomId
     */
    private void stubClassroomMappings(Map<UUID, UUID> map) {
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(2);
            for (Map.Entry<UUID, UUID> entry : map.entrySet()) {
                ResultSet rs = mock(ResultSet.class);
                when(rs.getString("student_id")).thenReturn(entry.getKey().toString());
                when(rs.getString("classroom_id")).thenReturn(entry.getValue().toString());
                handler.processRow(rs);
            }
            return null;
        }).when(namedParameterJdbcTemplate)
                .query(anyString(), any(SqlParameterSource.class), any(RowCallbackHandler.class));
    }

    @Test
    @DisplayName("Tie (eşit puan) standart yarışma sıralaması üretir: 300,300,250,200 -> 1,1,3,4")
    void finalizeExam_tieRanking_producesStandardCompetitionRanks() {
        UUID examId = UUID.randomUUID();
        Exam exam = openExam(examId);

        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        UUID s3 = UUID.randomUUID();
        UUID s4 = UUID.randomUUID();

        StudentExamResult r1 = approved(s1, "300.00");
        StudentExamResult r2 = approved(s2, "300.00");
        StudentExamResult r3 = approved(s3, "250.00");
        StudentExamResult r4 = approved(s4, "200.00");

        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(studentExamResultRepository.findByExamIdAndStatusOrderByTotalScoreDesc(examId, StudentExamResult.ResultStatus.APPROVED))
                .thenReturn(List.of(r1, r2, r3, r4));

        UUID class1 = UUID.randomUUID();
        UUID class2 = UUID.randomUUID();
        // r1, r2, r3 aynı sınıfta; r4 başka sınıfta
        stubClassroomMappings(Map.of(s1, class1, s2, class1, s3, class1, s4, class2));

        examFinalizationService.finalizeExam(examId);

        assertEquals(Exam.ExamStatus.FINALIZED, exam.getStatus());
        assertNotNull(exam.getFinalizedAt());

        assertEquals(1, r1.getSchoolRank());
        assertEquals(1, r2.getSchoolRank());
        assertEquals(3, r3.getSchoolRank());
        assertEquals(4, r4.getSchoolRank());

        assertEquals(1, r1.getClassRank());
        assertEquals(new BigDecimal("33.33"), r1.getPercentile());
        assertEquals(new BigDecimal("33.33"), r2.getPercentile());
        assertEquals(3, r3.getClassRank());
        assertEquals(new BigDecimal("100.00"), r3.getPercentile());

        assertEquals(1, r4.getClassRank());
        assertEquals(new BigDecimal("100.00"), r4.getPercentile());

        verify(studentExamResultRepository).saveAll(any());
        verify(kafkaTemplate).send(eq("exam-results-topic"), any(Map.class));
    }

    @Test
    @DisplayName("Tek öğrenci: sınıf sırası 1, yüzdelik dilim 100.00 (sınıf mevcudu 1)")
    void finalizeExam_singleStudent_ranksFirstWithFullPercentile() {
        UUID examId = UUID.randomUUID();
        Exam exam = openExam(examId);
        UUID student = UUID.randomUUID();
        StudentExamResult r = approved(student, "120.00");

        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(studentExamResultRepository.findByExamIdAndStatusOrderByTotalScoreDesc(examId, StudentExamResult.ResultStatus.APPROVED))
                .thenReturn(List.of(r));
        stubClassroomMappings(Map.of(student, UUID.randomUUID()));

        examFinalizationService.finalizeExam(examId);

        assertEquals(1, r.getSchoolRank());
        assertEquals(1, r.getClassRank());
        assertEquals(new BigDecimal("100.00"), r.getPercentile());
    }

    @Test
    @DisplayName("Herkes eşit puanda: hepsi 1. sırada, yüzdelik dilimleri eşit (1/3 = 33.33)")
    void finalizeExam_everyoneTied_allShareTopRank() {
        UUID examId = UUID.randomUUID();
        Exam exam = openExam(examId);
        UUID classId = UUID.randomUUID();

        StudentExamResult r1 = approved(UUID.randomUUID(), "200.00");
        StudentExamResult r2 = approved(UUID.randomUUID(), "200.00");
        StudentExamResult r3 = approved(UUID.randomUUID(), "200.00");

        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(studentExamResultRepository.findByExamIdAndStatusOrderByTotalScoreDesc(examId, StudentExamResult.ResultStatus.APPROVED))
                .thenReturn(List.of(r1, r2, r3));
        stubClassroomMappings(Map.of(
                r1.getStudentId(), classId,
                r2.getStudentId(), classId,
                r3.getStudentId(), classId));

        examFinalizationService.finalizeExam(examId);

        assertEquals(1, r1.getSchoolRank());
        assertEquals(1, r2.getSchoolRank());
        assertEquals(1, r3.getSchoolRank());
        assertEquals(1, r1.getClassRank());
        assertEquals(new BigDecimal("33.33"), r1.getPercentile());
        assertEquals(new BigDecimal("33.33"), r2.getPercentile());
        assertEquals(new BigDecimal("33.33"), r3.getPercentile());
    }

    @Test
    @DisplayName("Sınıfa atanmamış öğrencinin classRank ve percentile'ı null kalır, schoolRank set edilir")
    void finalizeExam_studentWithoutClassroom_hasNoClassRankOrPercentile() {
        UUID examId = UUID.randomUUID();
        Exam exam = openExam(examId);
        UUID inClass = UUID.randomUUID();
        UUID noClass = UUID.randomUUID();

        StudentExamResult r1 = approved(inClass, "300.00");
        StudentExamResult r2 = approved(noClass, "100.00");

        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(studentExamResultRepository.findByExamIdAndStatusOrderByTotalScoreDesc(examId, StudentExamResult.ResultStatus.APPROVED))
                .thenReturn(List.of(r1, r2));
        // yalnızca r1 bir sınıfa atanmış
        stubClassroomMappings(Map.of(inClass, UUID.randomUUID()));

        examFinalizationService.finalizeExam(examId);

        assertEquals(1, r1.getClassRank());
        assertEquals(2, r2.getSchoolRank());
        assertNull(r2.getClassRank());
        assertNull(r2.getPercentile());
    }

    @Test
    @DisplayName("Onaylı sonuç yoksa sınav yine FINALIZED olur, event yayınlanır, saveAll çağrılmaz")
    void finalizeExam_noApprovedResults_finalizesAndPublishes() {
        UUID examId = UUID.randomUUID();
        Exam exam = openExam(examId);

        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(studentExamResultRepository.findByExamIdAndStatusOrderByTotalScoreDesc(examId, StudentExamResult.ResultStatus.APPROVED))
                .thenReturn(List.of());

        examFinalizationService.finalizeExam(examId);

        assertEquals(Exam.ExamStatus.FINALIZED, exam.getStatus());
        verify(examRepository).save(exam);
        verify(studentExamResultRepository, never()).saveAll(any());
        verify(kafkaTemplate).send(eq("exam-results-topic"), any(Map.class));
    }

    @Test
    @DisplayName("Zaten FINALIZED sınavda tekrar finalize -> IllegalStateException")
    void finalizeExam_alreadyFinalized_throwsException() {
        UUID examId = UUID.randomUUID();
        Exam exam = new Exam();
        exam.setStatus(Exam.ExamStatus.FINALIZED);
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));

        assertThrows(IllegalStateException.class, () -> examFinalizationService.finalizeExam(examId));
    }

    @Test
    @DisplayName("Sınav bulunamazsa EntityNotFoundException")
    void finalizeExam_examNotFound_throwsEntityNotFound() {
        UUID examId = UUID.randomUUID();
        when(examRepository.findById(examId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> examFinalizationService.finalizeExam(examId));
    }

    @Test
    @DisplayName("Kontrat: finalize'ın yayınladığı topic, rozet consumer'ının dinlediği topic ile AYNI olmalı")
    void finalizeExam_publishedTopic_mustMatchBadgeConsumerTopic() throws Exception {
        UUID examId = UUID.randomUUID();
        Exam exam = openExam(examId);

        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));
        when(studentExamResultRepository.findByExamIdAndStatusOrderByTotalScoreDesc(examId, StudentExamResult.ResultStatus.APPROVED))
                .thenReturn(List.of());

        examFinalizationService.finalizeExam(examId);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), any(Map.class));
        String publishedTopic = topicCaptor.getValue();

        KafkaListener listener = ExamFinalizedBadgeConsumer.class
                .getMethod("consume", String.class)
                .getAnnotation(KafkaListener.class);
        String consumedTopic = listener.topics()[0];

        assertEquals(consumedTopic, publishedTopic,
                "EXAM_FINALIZED olayı '" + publishedTopic + "' topic'ine gidiyor ama rozet consumer'ı '"
                        + consumedTopic + "' dinliyor; bu uyuşmazlık yüzünden rozetler hiçbir zaman dağıtılmaz.");
    }
}
