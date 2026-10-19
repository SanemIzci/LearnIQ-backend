package com.learniq.exam.service;

import com.learniq.exam.domain.AiReport;
import com.learniq.exam.domain.Exam;
import com.learniq.exam.domain.StudentExamResult;
import com.learniq.exam.domain.StudentSubjectResult;
import com.learniq.exam.domain.Subject;
import com.learniq.exam.dto.AiReportResponseDto;
import com.learniq.exam.exception.EntityNotFoundException;
import com.learniq.exam.repository.AiReportRepository;
import com.learniq.exam.repository.StudentExamResultRepository;
import com.learniq.exam.repository.StudentSubjectResultRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiReportServiceTest {

    @Mock
    private AiReportRepository aiReportRepository;

    @Mock
    private StudentExamResultRepository examResultRepository;

    @Mock
    private StudentSubjectResultRepository subjectResultRepository;

    @Mock
    private DataFingerprintService fingerprintService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private AiReportService aiReportService;

    private static final UUID EXAM_ID = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();

    private StudentExamResult examResult(Exam.ExamStatus examStatus, StudentExamResult.ResultStatus resultStatus) {
        Exam exam = new Exam();
        exam.setStatus(examStatus);

        StudentExamResult result = new StudentExamResult();
        result.setExam(exam);
        result.setStatus(resultStatus);
        result.setTotalScore(new BigDecimal("100.00"));
        result.setPercentile(new BigDecimal("5.00"));
        return result;
    }

    private StudentSubjectResult subjectResult(String name, int correct, int wrong, String net) {
        return StudentSubjectResult.builder()
                .subject(Subject.builder().name(name).build())
                .correctCount(correct)
                .wrongCount(wrong)
                .netScore(new BigDecimal(net))
                .build();
    }

    @Test
    @DisplayName("Aynı hash ile READY rapor varsa kopyalanır; LLM/Kafka çağrılmaz")
    void requestReport_globalClone_success() {
        when(examResultRepository.findByExamIdAndStudentId(EXAM_ID, STUDENT_ID))
                .thenReturn(Optional.of(examResult(Exam.ExamStatus.FINALIZED, StudentExamResult.ResultStatus.APPROVED)));
        when(subjectResultRepository.findByStudentIdAndExamId(STUDENT_ID, EXAM_ID)).thenReturn(List.of());
        when(fingerprintService.generateHash(anyString())).thenReturn("same-hash");
        when(aiReportRepository.findByStudentIdAndDataHash(STUDENT_ID, "same-hash")).thenReturn(Optional.empty());

        AiReport other = AiReport.builder()
                .s3Key("s3/path/report.md")
                .summary("Harika bir iş çıkardın")
                .status(AiReport.ReportStatus.READY)
                .build();
        when(aiReportRepository.findFirstReadyByDataHash("same-hash")).thenReturn(Optional.of(other));

        AiReportResponseDto response = aiReportService.requestReport(EXAM_ID, STUDENT_ID);

        assertEquals("READY", response.getStatus());
        assertEquals("Harika bir iş çıkardın", response.getSummary());
        assertEquals("s3/path/report.md", response.getS3Key());
        verify(aiReportRepository).save(any(AiReport.class));
        verify(kafkaTemplate, never()).send(anyString(), any(Map.class));
    }

    @Test
    @DisplayName("Yeni rapor: PENDING kaydedilir ve Kafka'ya talep gider")
    void requestReport_newReport_success() {
        when(examResultRepository.findByExamIdAndStudentId(EXAM_ID, STUDENT_ID))
                .thenReturn(Optional.of(examResult(Exam.ExamStatus.FINALIZED, StudentExamResult.ResultStatus.APPROVED)));
        when(subjectResultRepository.findByStudentIdAndExamId(STUDENT_ID, EXAM_ID)).thenReturn(List.of());
        when(fingerprintService.generateHash(anyString())).thenReturn("new-hash");
        when(aiReportRepository.findByStudentIdAndDataHash(STUDENT_ID, "new-hash")).thenReturn(Optional.empty());
        when(aiReportRepository.findFirstReadyByDataHash("new-hash")).thenReturn(Optional.empty());
        when(aiReportRepository.save(any(AiReport.class)))
                .thenReturn(AiReport.builder().id(UUID.randomUUID()).status(AiReport.ReportStatus.PENDING).build());

        AiReportResponseDto response = aiReportService.requestReport(EXAM_ID, STUDENT_ID);

        assertEquals("PENDING", response.getStatus());
        verify(aiReportRepository).save(any(AiReport.class));
        verify(kafkaTemplate).send(eq("ai-requests-topic"), any(Map.class));
    }

    @Test
    @DisplayName("Öğrencinin kendi raporu varsa doğrudan döner; tekrar kaydetmez/yayınlamaz")
    void requestReport_existingOwnReport_returnsImmediately() {
        when(examResultRepository.findByExamIdAndStudentId(EXAM_ID, STUDENT_ID))
                .thenReturn(Optional.of(examResult(Exam.ExamStatus.FINALIZED, StudentExamResult.ResultStatus.APPROVED)));
        when(subjectResultRepository.findByStudentIdAndExamId(STUDENT_ID, EXAM_ID)).thenReturn(List.of());
        when(fingerprintService.generateHash(anyString())).thenReturn("own-hash");

        AiReport own = AiReport.builder()
                .status(AiReport.ReportStatus.READY)
                .s3Key("s3/my/path.md")
                .build();
        when(aiReportRepository.findByStudentIdAndDataHash(STUDENT_ID, "own-hash")).thenReturn(Optional.of(own));

        AiReportResponseDto response = aiReportService.requestReport(EXAM_ID, STUDENT_ID);

        assertEquals("READY", response.getStatus());
        assertEquals("s3/my/path.md", response.getS3Key());
        verify(aiReportRepository, never()).save(any(AiReport.class));
        verify(kafkaTemplate, never()).send(anyString(), any(Map.class));
    }

    @Test
    @DisplayName("Kendi PENDING raporu varsa tekrar yayınlanmaz -> talep 'takılı' kalır (davranış belgeleme)")
    void requestReport_existingPendingOwnReport_isReturnedWithoutRepublish() {
        when(examResultRepository.findByExamIdAndStudentId(EXAM_ID, STUDENT_ID))
                .thenReturn(Optional.of(examResult(Exam.ExamStatus.FINALIZED, StudentExamResult.ResultStatus.APPROVED)));
        when(subjectResultRepository.findByStudentIdAndExamId(STUDENT_ID, EXAM_ID)).thenReturn(List.of());
        when(fingerprintService.generateHash(anyString())).thenReturn("pending-hash");

        AiReport pending = AiReport.builder().status(AiReport.ReportStatus.PENDING).build();
        when(aiReportRepository.findByStudentIdAndDataHash(STUDENT_ID, "pending-hash")).thenReturn(Optional.of(pending));

        AiReportResponseDto response = aiReportService.requestReport(EXAM_ID, STUDENT_ID);

        assertEquals("PENDING", response.getStatus());
        verify(kafkaTemplate, never()).send(anyString(), any(Map.class));
    }

    @Test
    @DisplayName("Sınav FINALIZED değilse IllegalStateException (repo'ya devam edilmez)")
    void requestReport_examNotFinalized_throws() {
        when(examResultRepository.findByExamIdAndStudentId(EXAM_ID, STUDENT_ID))
                .thenReturn(Optional.of(examResult(Exam.ExamStatus.OPEN, StudentExamResult.ResultStatus.APPROVED)));

        assertThrows(IllegalStateException.class, () -> aiReportService.requestReport(EXAM_ID, STUDENT_ID));
    }

    @Test
    @DisplayName("Sonuç APPROVED değilse IllegalStateException")
    void requestReport_resultNotApproved_throws() {
        when(examResultRepository.findByExamIdAndStudentId(EXAM_ID, STUDENT_ID))
                .thenReturn(Optional.of(examResult(Exam.ExamStatus.FINALIZED, StudentExamResult.ResultStatus.SUBMITTED)));

        assertThrows(IllegalStateException.class, () -> aiReportService.requestReport(EXAM_ID, STUDENT_ID));
    }

    @Test
    @DisplayName("Sonuç kaydı yoksa EntityNotFoundException")
    void requestReport_resultNotFound_throwsEntityNotFound() {
        when(examResultRepository.findByExamIdAndStudentId(EXAM_ID, STUDENT_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> aiReportService.requestReport(EXAM_ID, STUDENT_ID));
    }

    @Test
    @DisplayName("Fingerprint payload'ı dersleri isme göre deterministik sıralar")
    void requestReport_buildsDeterministicSortedPayload() {
        when(examResultRepository.findByExamIdAndStudentId(EXAM_ID, STUDENT_ID))
                .thenReturn(Optional.of(examResult(Exam.ExamStatus.FINALIZED, StudentExamResult.ResultStatus.APPROVED)));
        // Kasıtlı ters sırada veriliyor
        when(subjectResultRepository.findByStudentIdAndExamId(STUDENT_ID, EXAM_ID)).thenReturn(List.of(
                subjectResult("Türkçe", 20, 5, "18.75"),
                subjectResult("Matematik", 30, 2, "29.50")));
        when(fingerprintService.generateHash(anyString())).thenReturn("sorted-hash");
        when(aiReportRepository.findByStudentIdAndDataHash(STUDENT_ID, "sorted-hash")).thenReturn(Optional.empty());
        when(aiReportRepository.findFirstReadyByDataHash("sorted-hash")).thenReturn(Optional.empty());
        when(aiReportRepository.save(any(AiReport.class)))
                .thenReturn(AiReport.builder().id(UUID.randomUUID()).build());

        aiReportService.requestReport(EXAM_ID, STUDENT_ID);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(fingerprintService).generateHash(payloadCaptor.capture());
        String payload = payloadCaptor.getValue();

        int mathIdx = payload.indexOf("Matematik");
        int turkceIdx = payload.indexOf("Türkçe");
        assertTrue(mathIdx >= 0 && turkceIdx >= 0, "payload her iki dersi içermeli: " + payload);
        assertTrue(mathIdx < turkceIdx, "Matematik, Türkçe'den önce sıralanmalı: " + payload);
    }
}
