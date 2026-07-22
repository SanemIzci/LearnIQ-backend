package com.learniq.exam.service;

import com.learniq.common.event.ExamResultProcessedEvent;
import com.learniq.common.tenant.TenantContext;
import com.learniq.exam.domain.Exam;
import com.learniq.exam.domain.StudentExamResult;
import com.learniq.exam.domain.StudentSubjectResult;
import com.learniq.exam.domain.Subject;
import com.learniq.exam.dto.StudentExamResultRequest;
import com.learniq.exam.dto.SubjectResultDto;
import com.learniq.exam.exception.EntityNotFoundException;
import com.learniq.exam.repository.ExamRepository;
import com.learniq.exam.repository.StudentExamResultRepository;
import com.learniq.exam.repository.StudentSubjectResultRepository;
import com.learniq.exam.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExamResultService {

    private final ExamRepository examRepository;
    private final SubjectRepository subjectRepository;
    private final StudentExamResultRepository studentExamResultRepository;
    private final StudentSubjectResultRepository studentSubjectResultRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public void submitExamResult(UUID examId, StudentExamResultRequest request) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new EntityNotFoundException("Exam", examId));

        // Save Macro Exam Result
        StudentExamResult examResult = StudentExamResult.builder()
                .studentId(request.getStudentId())
                .exam(exam)
                .totalScore(request.getTotalScore())
                .classRank(request.getClassRank())
                .schoolRank(request.getSchoolRank())
                .build();

        studentExamResultRepository.save(examResult);

        // Save Micro Subject Results
        for (SubjectResultDto subjectResultDto : request.getSubjectResults()) {
            Subject subject = subjectRepository.findById(subjectResultDto.getSubjectId())
                    .orElseThrow(() -> new EntityNotFoundException("Subject", subjectResultDto.getSubjectId()));

            StudentSubjectResult subjectResult = StudentSubjectResult.builder()
                    .studentId(request.getStudentId())
                    .exam(exam)
                    .subject(subject)
                    .correctCount(subjectResultDto.getCorrectCount())
                    .wrongCount(subjectResultDto.getWrongCount())
                    .netScore(subjectResultDto.getNetScore())
                    .build();

            studentSubjectResultRepository.save(subjectResult);
        }

        // Emit Async Event to Kafka
        ExamResultProcessedEvent event = ExamResultProcessedEvent.builder()
                .tenantId(TenantContext.getCurrentTenant())
                .studentId(request.getStudentId())
                .examId(examId)
                .totalScore(request.getTotalScore())
                .build();

        kafkaTemplate.send("exam-results-topic", event);
    }
}
