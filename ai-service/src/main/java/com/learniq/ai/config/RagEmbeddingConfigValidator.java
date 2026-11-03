package com.learniq.ai.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Embedding konfigürasyonu tutarlılık kontrolü (startup fail-fast).
 *
 * Neden: retrieval'ın doğruluğu tek bir embedding uzayı varsayar. Yanlış boyut,
 * yanlış model imzası veya prod'da boş `allowed-model-sigs` sessiz kalite kaybı
 * (uyumsuz vektörlerin karışması) üretir. Bu bileşen bunu uygulama açılırken
 * yakalar; DB şemasındaki sabit kolon boyutu 1536 ile uyumu doğrular.
 *
 * Prod profilinde: API anahtarı ve izinli model imzası listesi zorunludur.
 * Dev/pilot: boş liste uyarı ile kabul edilir (geçiş uyumluluğu).
 */
@Slf4j
@Component
public class RagEmbeddingConfigValidator {

    /** V5/V7 migration'larındaki {@code embedding vector(1536)} ile sabittir. */
    static final int DOCUMENT_DIMENSIONS = 1536;

    private final String model;
    private final int dimensions;
    private final String modelSig;
    private final String allowedModelSigs;
    private final String apiKey;
    private final Environment environment;

    public RagEmbeddingConfigValidator(
            @Value("${rag.embedding.model:text-embedding-3-small}") String model,
            @Value("${rag.embedding.dimensions:1536}") int dimensions,
            @Value("${rag.embedding.model-sig:text-embedding-3-small@1536}") String modelSig,
            @Value("${rag.chat.allowed-model-sigs:}") String allowedModelSigs,
            @Value("${rag.embedding.api-key:}") String apiKey,
            Environment environment) {
        this.model = model;
        this.dimensions = dimensions;
        this.modelSig = modelSig;
        this.allowedModelSigs = allowedModelSigs;
        this.apiKey = apiKey;
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        if (dimensions != DOCUMENT_DIMENSIONS) {
            throw new IllegalStateException(
                    "rag.embedding.dimensions=%d geçersiz: rag_documents.embedding kolonu vector(%d). "
                            .formatted(dimensions, DOCUMENT_DIMENSIONS)
                            + "Boyut değişikliği migration + yeniden embedding gerektirir.");
        }
        if (modelSig == null || !modelSig.endsWith("@" + dimensions)) {
            throw new IllegalStateException(
                    "rag.embedding.model-sig='%s' boyutla tutarsız; '@%d' ile bitmelidir."
                            .formatted(modelSig, dimensions));
        }
        if (!modelSig.startsWith(model + "@")) {
            log.warn("rag.embedding.model-sig ('%s') ile rag.embedding.model ('%s') adı uyuşmuyor; "
                    + "imza yalnız bir etikettir, model geçişinde güncellendiğinden emin olun.",
                    modelSig, model);
        }

        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (!prod) {
            if (allowedModelSigs == null || allowedModelSigs.isBlank()) {
                log.warn("rag.chat.allowed-model-sigs boş: model filtresi KAPALI "
                        + "(yalnız dev/pilot için güvenli).");
            }
            return;
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Prod profilinde rag.embedding.api-key zorunludur (RAG_EMBEDDING_API_KEY).");
        }
        if (allowedModelSigs == null || allowedModelSigs.isBlank()) {
            throw new IllegalStateException(
                    "Prod profilinde rag.chat.allowed-model-sigs zorunludur; "
                            + "boş bırakmak embedding uzayı uyumsuzluğunu gizler.");
        }
        boolean includesCurrentSig = Arrays.stream(allowedModelSigs.split(","))
                .map(String::trim)
                .anyMatch(modelSig::equals);
        if (!includesCurrentSig) {
            throw new IllegalStateException(
                    "rag.chat.allowed-model-sigs mevcut rag.embedding.model-sig ('%s') değerini içermiyor."
                            .formatted(modelSig));
        }
        log.info("Embedding konfigürasyonu doğrulandı: model-sig={}, allowed-model-sigs={}",
                modelSig, allowedModelSigs);
    }
}
