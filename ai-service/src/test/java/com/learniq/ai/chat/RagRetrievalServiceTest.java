package com.learniq.ai.chat;

import com.learniq.ai.rag.RagDocumentRepository;
import com.learniq.ai.rag.RagMetrics;
import com.learniq.ai.rag.TenantSchemaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("RagRetrievalService Unit Tests")
class RagRetrievalServiceTest {

    private static final String TENANT = "akademi_1";

    @Mock
    private TenantSchemaService tenantSchemaService;

    @Mock
    private RagDocumentRepository ragDocumentRepository;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private RagMetrics ragMetrics;

    private RagRetrievalService ragRetrievalService;

    @BeforeEach
    void setUp() {
        ragRetrievalService = new RagRetrievalService(tenantSchemaService, ragDocumentRepository, embeddingModel, ragMetrics);
        ReflectionTestUtils.setField(ragRetrievalService, "topK", 5);
        ReflectionTestUtils.setField(ragRetrievalService, "similarityThreshold", 0.30);
        ReflectionTestUtils.setField(ragRetrievalService, "allowedModelSigs", "");
    }

    private RagDocumentRepository.RagHit hit(double similarity) {
        return new RagDocumentRepository.RagHit("TEACHER_OBSERVATION", UUID.randomUUID(),
                "gözlem", Map.of("classroomName", "12-A"), similarity, "text-embedding-3-small@1536");
    }

    @Test
    @DisplayName("retrieve - şemaya geçilir, soru embed edilir, eşik filtresi uygulanır")
    void retrieve_filtersByThreshold() {
        when(ragDocumentRepository.embeddingColumnExists(TENANT)).thenReturn(true);
        when(embeddingModel.embed("türev sorusu")).thenReturn(List.of(0.1, 0.2));
        when(ragDocumentRepository.search(eq(TENANT), eq(List.of(0.1, 0.2)), eq(null), eq(null), eq(5), eq(List.of())))
                .thenReturn(List.of(hit(0.82), hit(0.29)));

        List<RagDocumentRepository.RagHit> result =
                ragRetrievalService.retrieve(TENANT, "türev sorusu", null, null);

        verify(tenantSchemaService).switchTo(TENANT);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).similarity()).isEqualTo(0.82);
    }

    @Test
    @DisplayName("retrieve - pre-filter sınıf/ders kimlikleri repository'e iletilir")
    void retrieve_passesPreFilters() {
        UUID classroomId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        when(ragDocumentRepository.embeddingColumnExists(TENANT)).thenReturn(true);
        when(embeddingModel.embed(anyString())).thenReturn(List.of(0.5));
        when(ragDocumentRepository.search(eq(TENANT), anyList(), eq(classroomId), eq(subjectId), eq(5), eq(List.of())))
                .thenReturn(List.of());

        ragRetrievalService.retrieve(TENANT, "soru", classroomId, subjectId);

        verify(ragDocumentRepository).search(eq(TENANT), anyList(), eq(classroomId), eq(subjectId), eq(5), eq(List.of()));
    }

    @Test
    @DisplayName("retrieve - pgvector yoksa embedding çağrılmadan boş bağlam döner (graceful degrade)")
    void retrieve_noPgVector_degradesGracefully() {
        when(ragDocumentRepository.embeddingColumnExists(TENANT)).thenReturn(false);

        List<RagDocumentRepository.RagHit> result =
                ragRetrievalService.retrieve(TENANT, "soru", null, null);

        assertThat(result).isEmpty();
        verify(embeddingModel, never()).embed(anyString());
        verify(ragDocumentRepository, never()).search(anyString(), anyList(), any(), any(), anyInt(), anyList());
    }
}
