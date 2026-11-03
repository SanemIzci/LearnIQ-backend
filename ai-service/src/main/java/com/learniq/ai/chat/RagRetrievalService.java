package com.learniq.ai.chat;

import com.learniq.ai.rag.RagDocumentRepository;
import com.learniq.ai.rag.RagMetrics;
import com.learniq.ai.rag.TenantSchemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Admin RAG chat semantik retrieval katmanı.
 *
 * - Soru metni embedding'e çevrilir ve kiracı şemasındaki rag_documents
 *   üzerinde cosine benzerliği ile arama yapılır (önceden filtreleme: sınıf/ders).
 * - Tombstone'lanmış dokümanlar ve (tanımlıysa) izinli olmayan embedding model
 *   imzaları dışlanır.
 * - pgvector yoksa veya embedding sağlayıcısı hata verirse dengeli bozulma:
 *   boş bağlam döner; chat tool calling (sayısal fact) ile çalışmaya devam eder.
 * - Benzerlik eşiği altındaki hit'ler elenir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalService {

    private final TenantSchemaService tenantSchemaService;
    private final RagDocumentRepository ragDocumentRepository;
    private final EmbeddingModel embeddingModel;
    private final RagMetrics ragMetrics;

    @Value("${rag.chat.top-k:5}")
    private int topK;

    @Value("${rag.chat.similarity-threshold:0.30}")
    private double similarityThreshold;

    /**
     * İzinli embedding model imzaları (virgülle ayrık). Boşsa model filtresi
     * uygulanmaz — model geçiş penceresinde eski dokümanlar görünür kalır.
     */
    @Value("${rag.chat.allowed-model-sigs:}")
    private String allowedModelSigs;

    /**
     * @return benzerlik eşiğinden geçen dokümanlar (benzerlik DESC); pgvector yoksa veya
     *         embedding üretilemezse boş liste (chat tool-only modda devam eder).
     */
    public List<RagDocumentRepository.RagHit> retrieve(String tenantSchema,
                                                       String question,
                                                       UUID classroomId,
                                                       UUID subjectId) {
        tenantSchemaService.switchTo(tenantSchema);

        if (!ragDocumentRepository.embeddingColumnExists(tenantSchema)) {
            log.warn("pgvector bu kiracı şemasında aktif değil; semantik retrieval boş döndürülüyor. schema={}",
                    tenantSchema);
            return List.of();
        }

        List<Double> queryEmbedding;
        try {
            queryEmbedding = embeddingModel.embed(question);
        } catch (RuntimeException e) {
            // Embedding sağlayıcısı erişilemez: chat tool-only olarak devam eder.
            log.warn("Sorgu embedding'i üretilemedi; semantik bağlam atlanıyor: {}", e.getMessage());
            ragMetrics.retrievalEmpty();
            return List.of();
        }

        List<RagDocumentRepository.RagHit> hits = ragDocumentRepository.search(
                tenantSchema, queryEmbedding, classroomId, subjectId, topK, allowedModels());

        List<RagDocumentRepository.RagHit> filtered = hits.stream()
                .filter(hit -> hit.similarity() >= similarityThreshold)
                .toList();

        if (filtered.isEmpty()) {
            ragMetrics.retrievalEmpty();
        }
        if (hits.isEmpty() || filtered.size() < hits.size()) {
            ragMetrics.retrievalModelMismatch();
        }
        log.info("Semantik retrieval tamamlandı: schema={} → ham={}, eşik sonrası={}",
                tenantSchema, hits.size(), filtered.size());
        return filtered;
    }

    private List<String> allowedModels() {
        if (allowedModelSigs == null || allowedModelSigs.isBlank()) {
            return List.of();
        }
        return Arrays.stream(allowedModelSigs.split(","))
                .map(String::trim)
                .filter(sig -> !sig.isEmpty())
                .toList();
    }
}
