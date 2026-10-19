package com.learniq.exam.service;

import com.learniq.exam.domain.Exam;
import com.learniq.exam.domain.StudentExamResult;
import com.learniq.exam.domain.Subject;
import com.learniq.exam.dto.ExamResultSubmitRequest;
import com.learniq.exam.dto.SubjectInputDto;
import com.learniq.exam.exception.EntityNotFoundException;
import com.learniq.exam.repository.ExamRepository;
import com.learniq.exam.repository.StudentExamResultRepository;
import com.learniq.exam.repository.StudentSubjectResultRepository;
import com.learniq.exam.repository.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ExamResultServiceTest {

    @Mock private ExamRepository examRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private StudentExamResultRepository studentExamResultRepository;
    @Mock private StudentSubjectResultRepository studentSubjectResultRepository;
    @Mock private NetCalculator netCalculator;

    @InjectMocks
    private ExamResultService examResultService;

    private UUID examId;
    private UUID studentId;
    private UUID subjectId;
    private Exam mockExam;
    private Subject mockSubject;

    @BeforeEach
    void setUp() {
        examId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        subjectId = UUID.randomUUID();

        mockExam = Exam.builder().id(examId).examType("TYT").status(Exam.ExamStatus.OPEN).build();
        mockSubject = Subject.builder().id(subjectId).name("Matematik").build();
    }

    @Test
    void submitResult_success_savesSubmittedResult() {
        // Arrange
        ExamResultSubmitRequest request = new ExamResultSubmitRequest();
        SubjectInputDto input = new SubjectInputDto();
        input.setSubjectId(subjectId);
        input.setCorrectCount(30);
        input.setWrongCount(4);
        request.setSubjects(List.of(input));

        when(examRepository.findById(examId)).thenReturn(Optional.of(mockExam));
        when(subjectRepository.findById(subjectId)).thenReturn(Optional.of(mockSubject));
        when(netCalculator.calculateNetScore(any(), any(), anyInt(), anyInt())).thenReturn(BigDecimal.valueOf(29));
        when(studentExamResultRepository.findByExamIdAndStudentId(examId, studentId)).thenReturn(Optional.empty());

        // Act
        examResultService.submitResult(examId, studentId, request);

        // Assert
        ArgumentCaptor<StudentExamResult> resultCaptor = ArgumentCaptor.forClass(StudentExamResult.class);
        verify(studentExamResultRepository).save(resultCaptor.capture());

        StudentExamResult savedResult = resultCaptor.getValue();
        assertThat(savedResult.getStatus()).isEqualTo(StudentExamResult.ResultStatus.SUBMITTED);
        assertThat(savedResult.getTotalScore()).isEqualByComparingTo(BigDecimal.valueOf(29));
        assertThat(savedResult.getEntryMethod()).isEqualTo(StudentExamResult.EntryMethod.MANUAL);
    }

    @Test
    void submitResult_examClosed_throwsException() {
        mockExam.setStatus(Exam.ExamStatus.FINALIZED);
        when(examRepository.findById(examId)).thenReturn(Optional.of(mockExam));

        ExamResultSubmitRequest request = new ExamResultSubmitRequest();

        assertThatThrownBy(() -> examResultService.submitResult(examId, studentId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Sınav kapalı");
    }

    @Test
    void approveResult_success_updatesStatus() {
        UUID teacherId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        StudentExamResult result = StudentExamResult.builder()
                .id(resultId)
                .exam(mockExam)
                .status(StudentExamResult.ResultStatus.SUBMITTED)
                .build();

        when(studentExamResultRepository.findById(resultId)).thenReturn(Optional.of(result));

        examResultService.approveResult(examId, resultId, teacherId);

        verify(studentExamResultRepository).save(result);
        assertThat(result.getStatus()).isEqualTo(StudentExamResult.ResultStatus.APPROVED);
        assertThat(result.getReviewedBy()).isEqualTo(teacherId);
        assertThat(result.getReviewedAt()).isNotNull();
    }
}
