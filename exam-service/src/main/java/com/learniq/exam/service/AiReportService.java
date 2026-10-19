package com.learniq.exam.service;

import com.learniq.exam.domain.AiReport;
import com.learniq.exam.domain.Exam;
import com.learniq.exam.domain.StudentExamResult;
import com.learniq.exam.domain.StudentSubjectResult;
import com.learniq.exam.dto.AiReportResponseDto;
import com.learniq.exam.exception.EntityNotFoundException;
import com.learniq.exam.repository.AiReportRepository;
import com.learniq.exam.repository.StudentExamResultRepository;
import com.learniq.exam.repository.StudentSubjectResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiReportService {

    private final AiReportRepository aiReportRepository;
    private final StudentExamResultRepository examResultRepository;
    private final StudentSubjectResultRepository subjectResultRepository;
    private final DataFingerprintService fingerprintService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public AiReportResponseDto requestReport(UUID examId, UUID studentId) {
        StudentExamResult examResult = examResultRepository.findByExamIdAndStudentId(examId, studentId)
                .orElseThrow(() -> new EntityNotFoundException("Result", studentId));

        if (examResult.getExam().getStatus() != Exam.ExamStatus.FINALIZED || examResult.getStatus() != StudentExamResult.ResultStatus.APPROVED) {
            throw new IllegalStateException("Sınav onaylanmadan veya sonlandırılmadan yapay zeka raporu alınamaz.");
        }

        List<StudentSubjectResult> subjectResults = subjectResultRepository.findByStudentIdAndExamId(studentId, examId);
        
        String dataPayload = buildDataPayload(examResult, subjectResults);
        String dataHash = fingerprintService.generateHash(dataPayload);

        // 1. Öğrencinin zaten bu veriyle bir raporu var mı?
        Optional<AiReport> existingOwnReport = aiReportRepository.findByStudentIdAndDataHash(studentId, dataHash);
        if (existingOwnReport.isPresent()) {
            return mapToDto(existingOwnReport.get());
        }

        // 2. Başka bir öğrencinin aynı netlerle (aynı hash) hazırlanmış raporu var mı?
        Optional<AiReport> existingGlobalReadyReport = aiReportRepository.findFirstReadyByDataHash(dataHash);
        if (existingGlobalReadyReport.isPresent()) {
            log.info("Aynı nete sahip farklı bir öğrencinin raporu kopyalanıyor. Hash: {}", dataHash);
            AiReport cloned = AiReport.builder()
                    .studentId(studentId)
                    .dataHash(dataHash)
                    .status(AiReport.ReportStatus.READY)
                    .s3Key(existingGlobalReadyReport.get().getS3Key())
                    .summary(existingGlobalReadyReport.get().getSummary())
                    .examCount(1) // İleride birden fazla sınavı birleştiren raporlar için arttırılabilir
                    .completedAt(LocalDateTime.now())
                    .build();
            
            aiReportRepository.save(cloned);
            return mapToDto(cloned);
        }

        // 3. Yeni bir rapor talebi oluştur
        AiReport newReport = AiReport.builder()
                .studentId(studentId)
                .dataHash(dataHash)
                .status(AiReport.ReportStatus.PENDING)
                .build();
        newReport = aiReportRepository.save(newReport);

        // Kafka'ya asenkron talep gönder
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "AI_REPORT_REQUESTED");
        event.put("reportId", newReport.getId().toString());
        event.put("studentId", studentId.toString());
        event.put("tenantId", com.learniq.common.tenant.TenantContext.getCurrentTenant());
        event.put("payloadData", dataPayload);
        com.learniq.exam.messaging.KafkaAfterCommit.send(kafkaTemplate, "ai-requests-topic", event);
        
        log.info("Yeni AI Rapor talebi oluşturuldu. Hash: {}", dataHash);
        return mapToDto(newReport);
    }

    private String buildDataPayload(StudentExamResult examResult, List<StudentSubjectResult> subjectResults) {
        // Deterministic serialization of exam performance
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Total:%.2f|Percentile:%.2f", examResult.getTotalScore(), examResult.getPercentile()));
        
        // Sort subjects by name to ensure consistent order
        List<StudentSubjectResult> sortedSubjects = subjectResults.stream()
                .sorted(Comparator.comparing(s -> s.getSubject().getName()))
                .collect(Collectors.toList());

        for (StudentSubjectResult sr : sortedSubjects) {
            sb.append(String.format("|%s:C%dW%dN%.2f", 
                sr.getSubject().getName(), 
                sr.getCorrectCount(), 
                sr.getWrongCount(), 
                sr.getNetScore()));
        }
        return sb.toString();
    }

    private AiReportResponseDto mapToDto(AiReport report) {
        return AiReportResponseDto.builder()
                .id(report.getId() != null ? report.getId().toString() : null)
                .status(report.getStatus().name())
                .summary(report.getSummary())
                .s3Key(report.getS3Key())
                .build();
    }
}
