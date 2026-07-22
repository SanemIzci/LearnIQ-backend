package com.learniq.exam.service;

import com.learniq.common.event.ExamResultProcessedEvent;
import com.learniq.common.tenant.TenantContext;
import com.learniq.exam.domain.Exam;
import com.learniq.exam.domain.Subject;
import com.learniq.exam.dto.StudentExamResultRequest;
import com.learniq.exam.dto.SubjectResultDto;
import com.learniq.exam.exception.EntityNotFoundException;
import com.learniq.exam.repository.ExamRepository;
import com.learniq.exam.repository.StudentExamResultRepository;
import com.learniq.exam.repository.StudentSubjectResultRepository;
import com.learniq.exam.repository.SubjectRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExamResultService Unit Tests")
class ExamResultServiceTest {

    @Mock private ExamRepository examRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private StudentExamResultRepository studentExamResultRepository;
    @Mock private StudentSubjectResultRepository studentSubjectResultRepository;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private ExamResultService examResultService;

    // ── Shared fixtures ────────────────────────────────────────────────────────

    private static final UUID EXAM_ID    = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID STUDENT_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    private static final UUID SUBJECT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final String TENANT_ID = "akademi_1";

    private Exam mockExam;
    private Subject mockSubject;
    private StudentExamResultRequest validRequest;

    @BeforeEach
    void setUp() {
        // Seed the TenantContext just as the TenantInterceptor would in production
        TenantContext.setCurrentTenant(TENANT_ID);

        mockExam = Exam.builder()
                .id(EXAM_ID)
                .title("Haziran TYT Denemesi")
                .examDate(LocalDate.of(2025, 6, 15))
                .examType("TYT")
                .build();

        mockSubject = Subject.builder()
                .id(SUBJECT_ID)
                .name("Mathematics")
                .build();

        SubjectResultDto subjectResultDto = new SubjectResultDto();
        subjectResultDto.setSubjectId(SUBJECT_ID);
        subjectResultDto.setCorrectCount(28);
        subjectResultDto.setWrongCount(4);
        subjectResultDto.setNetScore(new BigDecimal("27.00"));

        validRequest = new StudentExamResultRequest();
        validRequest.setStudentId(STUDENT_ID);
        validRequest.setTotalScore(new BigDecimal("312.75"));
        validRequest.setClassRank(2);
        validRequest.setSchoolRank(8);
        validRequest.setSubjectResults(List.of(subjectResultDto));
    }

    @AfterEach
    void tearDown() {
        // Always clear TenantContext to prevent test pollution
        TenantContext.clear();
    }

    // ── Success scenario ───────────────────────────────────────────────────────

    @Test
    @DisplayName("submitExamResult: should save macro result, save subject results, and publish Kafka event")
    void submitExamResult_success_savesResultsAndPublishesEvent() {
        // Arrange
        when(examRepository.findById(EXAM_ID)).thenReturn(Optional.of(mockExam));
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Optional.of(mockSubject));
        when(studentExamResultRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(studentSubjectResultRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        examResultService.submitExamResult(EXAM_ID, validRequest);

        // Assert: repositories were called exactly once each
        verify(studentExamResultRepository, times(1)).save(any());
        verify(studentSubjectResultRepository, times(1)).save(any());

        // Assert: Kafka event was sent to the correct topic with correct payload
        ArgumentCaptor<ExamResultProcessedEvent> eventCaptor =
                ArgumentCaptor.forClass(ExamResultProcessedEvent.class);

        verify(kafkaTemplate, times(1)).send(eq("exam-results-topic"), eventCaptor.capture());

        ExamResultProcessedEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(capturedEvent.getStudentId()).isEqualTo(STUDENT_ID);
        assertThat(capturedEvent.getExamId()).isEqualTo(EXAM_ID);
        assertThat(capturedEvent.getTotalScore()).isEqualByComparingTo("312.75");
    }

    // ── Failure scenario: invalid examId ───────────────────────────────────────

    @Test
    @DisplayName("submitExamResult: should throw EntityNotFoundException when exam does not exist")
    void submitExamResult_invalidExamId_throwsEntityNotFoundException() {
        // Arrange: repository returns empty Optional
        UUID unknownExamId = UUID.randomUUID();
        when(examRepository.findById(unknownExamId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> examResultService.submitExamResult(unknownExamId, validRequest))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Exam")
                .hasMessageContaining(unknownExamId.toString());

        // Assert: No data was saved, no Kafka event was emitted
        verifyNoInteractions(studentExamResultRepository);
        verifyNoInteractions(studentSubjectResultRepository);
        verifyNoInteractions(kafkaTemplate);
    }

    // ── Failure scenario: invalid subjectId ────────────────────────────────────

    @Test
    @DisplayName("submitExamResult: should throw EntityNotFoundException when subject does not exist")
    void submitExamResult_invalidSubjectId_throwsEntityNotFoundException() {
        // Arrange
        UUID unknownSubjectId = UUID.randomUUID();
        SubjectResultDto badDto = new SubjectResultDto();
        badDto.setSubjectId(unknownSubjectId);
        badDto.setCorrectCount(10);
        badDto.setWrongCount(2);
        badDto.setNetScore(new BigDecimal("9.50"));

        validRequest.setSubjectResults(List.of(badDto));

        when(examRepository.findById(EXAM_ID)).thenReturn(Optional.of(mockExam));
        when(subjectRepository.findById(unknownSubjectId)).thenReturn(Optional.empty());
        when(studentExamResultRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act & Assert
        assertThatThrownBy(() -> examResultService.submitExamResult(EXAM_ID, validRequest))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Subject")
                .hasMessageContaining(unknownSubjectId.toString());

        // Assert: macro result was saved but Kafka was NOT triggered (tx rolled back)
        verify(studentExamResultRepository, times(1)).save(any());
        verifyNoInteractions(kafkaTemplate);
    }
}
