package com.learniq.exam.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClassSubjectTrendCalculator Unit Tests")
class ClassSubjectTrendCalculatorTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks private ClassSubjectTrendCalculator calculator;

    private final String tenantId = "tenant_test";
    private UUID examId;
    private UUID classroomId;
    private UUID subjectId;

    @BeforeEach
    void setUp() {
        examId      = UUID.randomUUID();
        classroomId = UUID.randomUUID();
        subjectId   = UUID.randomUUID();
    }

    // ── Helper: classroom var, subject stats setup ─────────────────────────

    private void stubSchemaSet() {
        doNothing().when(jdbcTemplate).execute(anyString());
    }

    /** classroomIds sorgusu: queryForList(String, Class, Object...) — 1 vararg (examId) */
    @SuppressWarnings("unchecked")
    private void stubClassroomIds(List<UUID> ids) {
        doReturn(ids).when(jdbcTemplate)
                .queryForList(contains("DISTINCT cs.classroom_id"), eq(UUID.class), eq(examId));
    }

    /** expected_student_count sorgusu: queryForObject(String, Class, Object...) — 1 vararg (classroomId) */
    private void stubExpectedCount(int count) {
        when(jdbcTemplate.queryForObject(contains("classroom_students WHERE classroom_id"),
                eq(Integer.class), eq(classroomId)))
                .thenReturn(count);
    }

    /** calc_version sorgusu: queryForObject(String, Class, Object...) — 2 vararg */
    private void stubCalcVersion(long version) {
        when(jdbcTemplate.queryForObject(contains("COALESCE(MAX(calc_version)"),
                eq(Long.class), eq(classroomId), eq(examId)))
                .thenReturn(version);
    }

    /** subject stats sorgusu: queryForList(String, Object...) — 2 vararg (examId, classroomId) */
    private void stubSubjectStats(List<Map<String, Object>> rows) {
        doReturn(rows).when(jdbcTemplate)
                .queryForList(contains("student_subject_results"), eq(examId), eq(classroomId));
    }

    /** previous avg_net sorgusu: queryForList(String, Class, Object...) — 4 vararg */
    @SuppressWarnings("unchecked")
    private void stubPreviousAvgNet(List<BigDecimal> values) {
        doReturn(values).when(jdbcTemplate)
                .queryForList(contains("class_subject_trends"), eq(BigDecimal.class),
                        eq(classroomId), eq(subjectId), eq(examId), eq(examId));
    }

    /** upsert çağrısı: update(String, Object...) — 10 vararg; any() null dahil eşleşir (delta null senaryosu için) */
    private void stubUpsert() {
        doReturn(1).when(jdbcTemplate)
                .update(contains("class_subject_trends"),
                        any(), any(), any(), any(), any(),
                        anyInt(), anyInt(),
                        any(), nullable(Object.class), any(), anyLong());
    }

    private void stubKafka() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("calculate - onaylı sonuç yoksa hiçbir şey yapmaz")
    void calculate_noApprovedResults_skips() {
        stubSchemaSet();
        stubClassroomIds(Collections.emptyList());

        calculator.calculate(tenantId, examId);

        verify(jdbcTemplate, never()).update(anyString(), (Object[]) any());
    }

    @Test
    @DisplayName("calculate - sınıf bulunan durumda upsert çağrılır")
    void calculate_withClassroom_upsertCalled() {
        stubSchemaSet();
        stubClassroomIds(List.of(classroomId));
        stubExpectedCount(30);
        stubCalcVersion(1L);

        Map<String, Object> subjectRow = Map.of(
                "subject_id",    subjectId.toString(),
                "avg_net",       new BigDecimal("45.50"),
                "avg_score",     new BigDecimal("312.00"),
                "student_count", 28L
        );
        stubSubjectStats(List.of(subjectRow));
        stubPreviousAvgNet(Collections.emptyList()); // ilk sınav
        stubUpsert();
        stubKafka();

        calculator.calculate(tenantId, examId);

        verify(jdbcTemplate, atLeastOnce()).update(contains("class_subject_trends"),
                any(), any(), any(), any(), any(),
                anyInt(), anyInt(),
                any(), nullable(Object.class), any(), anyLong());
    }

    @Test
    @DisplayName("calculate - delta pozitif ise trend UP olarak upsert edilir")
    void trendDirection_up() {
        stubSchemaSet();
        stubClassroomIds(List.of(classroomId));
        stubExpectedCount(25);
        stubCalcVersion(1L);

        Map<String, Object> subjectRow = Map.of(
                "subject_id",    subjectId.toString(),
                "avg_net",       new BigDecimal("60.00"),
                "avg_score",     new BigDecimal("350.00"),
                "student_count", 24L
        );
        stubSubjectStats(List.of(subjectRow));
        // Önceki avg_net = 55.00 → delta = +5.00 → UP
        stubPreviousAvgNet(List.of(new BigDecimal("55.00")));
        stubUpsert();
        stubKafka();

        calculator.calculate(tenantId, examId);

        // trend_direction = "UP" argümanı doğrulanıyor
        verify(jdbcTemplate).update(anyString(), any(), any(), any(),
                any(), any(), anyInt(), anyInt(), any(), nullable(Object.class), eq("UP"), anyLong());
    }

    @Test
    @DisplayName("calculate - delta negatif ise trend DOWN olarak upsert edilir")
    void trendDirection_down() {
        stubSchemaSet();
        stubClassroomIds(List.of(classroomId));
        stubExpectedCount(25);
        stubCalcVersion(1L);

        Map<String, Object> subjectRow = Map.of(
                "subject_id",    subjectId.toString(),
                "avg_net",       new BigDecimal("40.00"),
                "avg_score",     new BigDecimal("310.00"),
                "student_count", 22L
        );
        stubSubjectStats(List.of(subjectRow));
        // Önceki avg_net = 48.00 → delta = -8.00 → DOWN
        stubPreviousAvgNet(List.of(new BigDecimal("48.00")));
        stubUpsert();
        stubKafka();

        calculator.calculate(tenantId, examId);

        verify(jdbcTemplate).update(anyString(), any(), any(), any(),
                any(), any(), anyInt(), anyInt(), any(), nullable(Object.class), eq("DOWN"), anyLong());
    }

    @Test
    @DisplayName("calculate - delta yoksa (ilk sınav) trend STABLE olarak upsert edilir")
    void trendDirection_stable_firstExam() {
        stubSchemaSet();
        stubClassroomIds(List.of(classroomId));
        stubExpectedCount(20);
        stubCalcVersion(1L);

        Map<String, Object> subjectRow = Map.of(
                "subject_id",    subjectId.toString(),
                "avg_net",       new BigDecimal("50.00"),
                "avg_score",     new BigDecimal("330.00"),
                "student_count", 20L
        );
        stubSubjectStats(List.of(subjectRow));
        stubPreviousAvgNet(Collections.emptyList()); // önceki yok → delta null → STABLE
        stubUpsert();
        stubKafka();

        calculator.calculate(tenantId, examId);

        verify(jdbcTemplate).update(anyString(), any(), any(), any(),
                any(), any(), anyInt(), anyInt(), any(), nullable(Object.class), eq("STABLE"), anyLong());
    }
}
