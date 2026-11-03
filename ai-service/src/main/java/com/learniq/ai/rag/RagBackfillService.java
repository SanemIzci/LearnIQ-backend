package com.learniq.ai.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Embedding model geçişi için batch re-embedding.
 *
 * Geçiş planı: yeni model imzası allowed-model-sigs listesine EKLENİR (eski
 * dokümanlar görünür kalır) → bu servis eski imzalı dokümanları yeni modelle
 * yeniden embed eder → coverage tamamlanınca eski imza listeden çıkarılır.
 * Böylece re-embed tamamlanmadan görünürlük kaybı (veri boşluğu) oluşmaz.
 *
 * Bu işlem doküman version'unu değiştirmez (içerik aynıdır); yalnız embedding
 * ve model imzası güncellenir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagBackfillService {

    private final TenantSchemaService tenantSchemaService;
    private final RagDocumentRepository ragDocumentRepository;
    private final EmbeddingModel embeddingModel;
    private final RagMetrics ragMetrics;

    @Value("${rag.embedding.model-sig:text-embedding-3-small@1536}")
    private String embeddingModelSig;

    @Value("${rag.embedding.dimensions:1536}")
    private Integer embeddingDims;

    public record BackfillResult(int candidates, int updated, int failed) {}

    public BackfillResult backfill(String tenantSchema, int batchSize) {
        tenantSchemaService.switchTo(tenantSchema);
        int effectiveBatch = batchSize > 0 ? batchSize : 50;

        List<RagDocumentRepository.BackfillCandidate> candidates =
                ragDocumentRepository.findBackfillCandidates(tenantSchema, embeddingModelSig, effectiveBatch);

        int updated = 0;
        int failed = 0;
        for (RagDocumentRepository.BackfillCandidate candidate : candidates) {
            try {
                List<Double> embedding = embeddingModel.embed(candidate.content());
                ragDocumentRepository.updateEmbedding(tenantSchema, candidate.sourceType(),
                        candidate.sourceId(), embedding, embeddingModelSig, embeddingDims);
                ragMetrics.embedded();
                updated++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("Backfill başarısız: source={}/{} → {}",
                        candidate.sourceType(), candidate.sourceId(), e.getMessage());
            }
        }
        log.info("Backfill tamamlandı: schema={} model={} aday={} updated={} failed={}",
                tenantSchema, embeddingModelSig, candidates.size(), updated, failed);
        return new BackfillResult(candidates.size(), updated, failed);
    }
}
