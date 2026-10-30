package com.learniq.ai.chat;

import com.learniq.ai.rag.TenantSchemaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminChatToolService Unit Tests")
class AdminChatToolServiceTest {

    private static final String TENANT = "akademi_1";

    @Mock
    private TenantSchemaService tenantSchemaService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private AdminChatToolService toolService;

    private final UUID classroomId = UUID.randomUUID();
    private final UUID examId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        toolService = new AdminChatToolService(tenantSchemaService, jdbcTemplate);
        ReflectionTestUtils.setField(toolService, "defaultAtRiskNetThreshold", 15.0);
    }

    private void stubLatestExam(UUID id) {
        when(jdbcTemplate.queryForObject(contains(TENANT + ".class_subject_trends"), eq(UUID.class),
                eq(classroomId))).thenReturn(id);
    }

    @Test
    @DisplayName("classSubjectTrends - examId verilmezse son deneme seçilir, şema nitelikli SQL koşar")
    void classSubjectTrends_defaultsToLatestExam() {
        stubLatestExam(examId);
        when(jdbcTemplate.query(contains("FROM " + TENANT + ".class_subject_trends"), any(RowMapper.class),
                eq(classroomId), eq(examId))).thenReturn(List.of(
                new AdminChatToolService.ClassroomSubjectTrend(classroomId, examId, UUID.randomUUID(),
                        "12-A", "Matematik", "TYT Deneme 3", "2026-10-20",
                        BigDecimal.valueOf(24.6), null, 28,
                        BigDecimal.valueOf(93.33), BigDecimal.valueOf(1.4), "UP")));

        List<AdminChatToolService.ClassroomSubjectTrend> rows =
                toolService.classSubjectTrends(TENANT, classroomId, null, null);

        verify(tenantSchemaService).switchTo(TENANT);
        verify(jdbcTemplate).queryForObject(anyString(), eq(UUID.class), eq(classroomId));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).avgNet()).isEqualByComparingTo("24.6");
    }

    @Test
    @DisplayName("classSubjectTrends - sınıfın hiç trend satırı yoksa boş döner (ikinci sorguya geçilmez)")
    void classSubjectTrends_noExam_returnsEmpty() {
        when(jdbcTemplate.queryForObject(anyString(), eq(UUID.class), eq(classroomId)))
                .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));

        List<AdminChatToolService.ClassroomSubjectTrend> rows =
                toolService.classSubjectTrends(TENANT, classroomId, null, null);

        assertThat(rows).isEmpty();
        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), any(), any());
    }

    @Test
    @DisplayName("classSubjectTrends - subjectId filtresi eşik kolonu ekler")
    void classSubjectTrends_subjectPreFilter() {
        when(jdbcTemplate.query(contains("cst.subject_id = ?"), any(RowMapper.class),
                eq(classroomId), eq(examId), any(UUID.class))).thenReturn(List.of());

        toolService.classSubjectTrends(TENANT, classroomId, examId, UUID.randomUUID());

        verify(jdbcTemplate).query(contains("cst.subject_id = ?"), any(RowMapper.class),
                eq(classroomId), eq(examId), any(UUID.class));
    }

    @Test
    @DisplayName("atRiskStudents - verilen eşiğe kadar parametre kuyruğu doğru kurulur")
    void atRiskStudents_withThreshold() {
        when(jdbcTemplate.query(contains(TENANT + ".student_exam_results"), any(RowMapper.class),
                eq(examId), eq(classroomId), eq(10.0))).thenReturn(List.of(
                new AdminChatToolService.AtRiskStudent(UUID.randomUUID(), "Ali Veli",
                        BigDecimal.valueOf(7.5), BigDecimal.valueOf(180), 22)));

        List<AdminChatToolService.AtRiskStudent> rows =
                toolService.atRiskStudents(TENANT, classroomId, examId, 10.0);

        verify(tenantSchemaService).switchTo(TENANT);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).fullName()).isEqualTo("Ali Veli");
    }

    @Test
    @DisplayName("atRiskStudents - null eşik → konfigürasyondaki varsayılan (15.0) kullanılır")
    void atRiskStudents_nullThreshold_usesDefault() {
        when(jdbcTemplate.query(contains(TENANT + ".student_exam_results"), any(RowMapper.class),
                eq(examId), eq(classroomId), eq(15.0))).thenReturn(List.of());

        toolService.atRiskStudents(TENANT, classroomId, examId, null);

        verify(jdbcTemplate).query(contains(TENANT + ".student_exam_results"), any(RowMapper.class),
                eq(examId), eq(classroomId), eq(15.0));
    }

    @Test
    @DisplayName("atRiskStudents - deneme yoksa boş döner")
    void atRiskStudents_noExam_returnsEmpty() {
        when(jdbcTemplate.queryForObject(anyString(), eq(UUID.class), eq(classroomId)))
                .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));

        List<AdminChatToolService.AtRiskStudent> rows =
                toolService.atRiskStudents(TENANT, classroomId, null, null);

        assertThat(rows).isEmpty();
        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), any(), any(), any());
    }

    @Test
    @DisplayName("compareClassrooms - boş liste hiçbir sorguya geçmez")
    void compareClassrooms_emptyList_returnsEmpty() {
        List<AdminChatToolService.ClassroomComparison> rows =
                toolService.compareClassrooms(TENANT, List.of(), null);

        assertThat(rows).isEmpty();
        verify(tenantSchemaService).switchTo(TENANT);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("compareClassrooms - IN (?,?) cümlesi + exam parametresi doğru kurulur")
    void compareClassrooms_inClause() {
        UUID classroom2 = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(contains(TENANT + ".class_subject_trends"), eq(UUID.class),
                eq(classroomId), eq(classroom2)))
                .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));

        List<AdminChatToolService.ClassroomComparison> rows =
                toolService.compareClassrooms(TENANT, List.of(classroomId, classroom2), null);

        assertThat(rows).isEmpty();
        verify(jdbcTemplate).queryForObject(anyString(), eq(UUID.class), eq(classroomId), eq(classroom2));
    }
}
