package com.learniq.exam.service;

import com.learniq.exam.domain.StudentExamResult;
import com.learniq.exam.domain.StudentSubjectResult;
import com.learniq.common.dto.ExamPerformanceDto;
import com.learniq.common.dto.StudentTrendReportDto;
import com.learniq.common.dto.SubjectNetScoreDto;
import com.learniq.exam.repository.StudentExamResultRepository;
import com.learniq.exam.repository.StudentSubjectResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final StudentExamResultRepository studentExamResultRepository;
    private final StudentSubjectResultRepository studentSubjectResultRepository;

    @Transactional(readOnly = true)
    public StudentTrendReportDto getStudentTrendReport(UUID studentId) {
        log.info("Fetching trend report for studentId: {}", studentId);

        // 1. Fetch all macro exam results ordered chronologically (JOIN FETCH avoids N+1)
        List<StudentExamResult> examResults =
                studentExamResultRepository.findByStudentIdOrderByExamDateAsc(studentId);

        // 2. For each exam result, fetch the granular subject scores and map into DTOs
        List<ExamPerformanceDto> examHistory = examResults.stream()
                .map(result -> {
                    List<StudentSubjectResult> subjectResults =
                            studentSubjectResultRepository.findByStudentIdAndExamId(
                                    studentId, result.getExam().getId());

                    List<SubjectNetScoreDto> subjectScoreDtos = subjectResults.stream()
                            .map(sr -> SubjectNetScoreDto.builder()
                                    .subjectId(sr.getSubject().getId())
                                    .subjectName(sr.getSubject().getName())
                                    .correctCount(sr.getCorrectCount())
                                    .wrongCount(sr.getWrongCount())
                                    .netScore(sr.getNetScore())
                                    .build())
                            .toList();

                    return ExamPerformanceDto.builder()
                            .examId(result.getExam().getId())
                            .examTitle(result.getExam().getTitle())
                            .examType(result.getExam().getExamType())
                            .examDate(result.getExam().getExamDate())
                            .totalScore(result.getTotalScore())
                            .classRank(result.getClassRank())
                            .schoolRank(result.getSchoolRank())
                            .subjectScores(subjectScoreDtos)
                            .build();
                })
                .toList();

        // 3. Assemble the root report DTO
        return StudentTrendReportDto.builder()
                .studentId(studentId)
                .totalExamsTaken(examHistory.size())
                .examHistory(examHistory)
                .build();
    }
}
