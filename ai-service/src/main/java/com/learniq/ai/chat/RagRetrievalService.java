package com.learniq.ai.chat;

import com.learniq.ai.rag.RagDocumentRepository;
import com.learniq.ai.rag.TenantSchemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Admin RAG chat semantik retrieval katmanı.
 *
 * - Soru metni embedding'e çevrilir ve kiracı şemasındaki rag_documents
 *   üzerinde cosine benzerliği ile arama yapılır (önceden filtreleme: sınıf/ders).
 * - pgvector aktif değilse dengeli bozulma: boş bağlam döner ve chat tool
 *   calling (sayısal fact) ile çalışmaya devam eder.
 * - Benzerlik eşiği altındaki hit'ler elenir; böylece zayıf eşleşmeler LLM
 *   bağlamına karışıp yanıtı bozmaz.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalService {

    private final TenantSchemaService tenantSchemaService;
    private final RagDocumentRepository ragDocumentRepository;
    private final EmbeddingModel embeddingModel;

    @Value("${rag.chat.top-k:5}")
    private int topK;

    @Value("${rag.chat.similarity-threshold:0.30}")
    private double similarityThreshold;

    /**
     * @return benzerlik eşiğinden geçen dokümanlar (benzerlik DESC); pgvector yoksa boş liste.
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

        List<Double> queryEmbedding = embeddingModel.embed(question);
        List<RagDocumentRepository.RagHit> hits =
                ragDocumentRepository.search(tenantSchema, queryEmbedding, classroomId, subjectId, topK);

        List<RagDocumentRepository.RagHit> filtered = hits.stream()
                .filter(hit -> hit.similarity() >= similarityThreshold)
                .toList();
        log.info("Semantik retrieval tamamlandı: schema={} → ham={}, eşik sonrası={}",
                tenantSchema, hits.size(), filtered.size());
        return filtered;
    }
}
