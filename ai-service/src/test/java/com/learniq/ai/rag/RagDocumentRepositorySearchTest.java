package com.learniq.ai.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RagDocumentRepository.search Unit Tests")
class RagDocumentRepositorySearchTest {

    private static final String TENANT = "akademi_1";

    @Mock
    private JdbcTemplate jdbcTemplate;

    private RagDocumentRepository repository;

    private RagDocumentRepository repo() {
        if (repository == null) {
            repository = new RagDocumentRepository(jdbcTemplate, new ObjectMapper());
        }
        return repository;
    }

    @Test
    @DisplayName("search - şema nitelikli tablo + pre-filter yok + vector literal ilk parametre")
    void search_noFilters_schemaQualifiedSql() throws Exception {
        stubRowCallback();
        List<Double> embedding = List.of(0.1, 0.2);

        List<RagDocumentRepository.RagHit> hits =
                repo().search(TENANT, embedding, null, null, 5);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowCallbackHandler.class), argsCaptor.capture());

        assertThat(sqlCaptor.getValue()).contains(TENANT + ".rag_documents");
        assertThat(sqlCaptor.getValue()).contains("1 - (embedding <=> ?::vector)");
        assertThat(sqlCaptor.getValue()).doesNotContain("classroom_id = ?");
        assertThat(sqlCaptor.getValue()).doesNotContain("subject_id = ?");

        Object[] args = argsCaptor.getValue();
        assertThat(args).hasSize(2);
        assertThat(args[0]).isEqualTo("[0.100000,0.200000]"); // 6 hane literal
        assertThat(args[1]).isEqualTo(5);

        assertThat(hits).hasSize(1);
        RagDocumentRepository.RagHit hit = hits.get(0);
        assertThat(hit.sourceType()).isEqualTo("TEACHER_OBSERVATION");
        assertThat(hit.metadata()).containsEntry("classroomName", "12-A");
        assertThat(hit.similarity()).isEqualTo(0.83,
                org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("search - classroom/subject pre-filter koşulları SQL'e eklenir")
    void search_withPrefilter() {
        List<Double> embedding = List.of(0.1);

        repo().search(TENANT, embedding, UUID.randomUUID(), UUID.randomUUID(), 3);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowCallbackHandler.class), argsCaptor.capture());

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("AND classroom_id = ?");
        assertThat(sql).contains("AND subject_id = ?");
        assertThat(argsCaptor.getValue()).hasSize(4); // embedding + classroom + subject + topK
    }

    @Test
    @DisplayName("search - bozuk metadata JSON → boş metadata ile sonuç dönülmeye devam eder")
    void search_invalidMetadata_graceful() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("metadata")).thenReturn("BOZUK_JSON");
        when(rs.getString("source_type")).thenReturn("CLASS_ANALYSIS_FACT");
        when(rs.getString("source_id")).thenReturn(UUID.randomUUID().toString());
        when(rs.getString("content")).thenReturn("içerik");
        when(rs.getDouble("similarity")).thenReturn(0.5);

        doAnswer(inv -> {
            RowCallbackHandler handler = inv.getArgument(1);
            handler.processRow(rs);
            return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class),
                any(), any());

        List<RagDocumentRepository.RagHit> hits =
                repo().search(TENANT, List.of(0.1), null, null, 5);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).metadata()).isEmpty();
    }

    private void stubRowCallback() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("source_type")).thenReturn("TEACHER_OBSERVATION");
        when(rs.getString("source_id")).thenReturn(UUID.randomUUID().toString());
        when(rs.getString("content")).thenReturn("Öğretmen gözlem notu.");
        when(rs.getString("metadata")).thenReturn("{\"classroomName\":\"12-A\"}");
        when(rs.getDouble("similarity")).thenReturn(0.83);

        doAnswer(inv -> {
            RowCallbackHandler handler = inv.getArgument(1);
            handler.processRow(rs);
            return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class),
                any(), any());
    }
}
