package com.learniq.ai.config;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Embedding provider'ı chat provider'ından bağımsız yapılandırır (F-04).
 *
 * Spring AI sürümünün (1.0.0-M1) ortak {@code spring.ai.openai.*} property'lerine
 * güvenmek yerine bağımsız bir {@link OpenAiApi} + {@link OpenAiEmbeddingModel}
 * bean'i üretilir. Chat istemcisi (ChatClient) değişmez.
 */
@Configuration
public class EmbeddingClientConfig {

    @Bean
    @Primary
    public EmbeddingModel ragEmbeddingModel(
            @Value("${rag.embedding.base-url:https://api.openai.com}") String baseUrl,
            @Value("${rag.embedding.api-key:}") String apiKey,
            @Value("${rag.embedding.model:text-embedding-3-small}") String model,
            @Value("${rag.embedding.dimensions:1536}") Integer dimensions) {

        OpenAiApi openAiApi = new OpenAiApi(baseUrl, apiKey);
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .withModel(model)
                .withDimensions(dimensions)
                .build();
        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);
    }
}
