package com.learniq.ai.dto;

import com.learniq.ai.rag.RagDocumentRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin chat yanıtı: LLM cevabı + yanıttaki bilginin kaynakları.
 * Kaynaklar UI'da atıf olarak gösterilir (denetlenebilir AI).
 */
public record AdminChatResponse(String answer, List<ChatSource> sources) {

    public record ChatSource(
            String sourceType,
            UUID sourceId,
            Map<String, Object> metadata,
            double similarity) {

        public static ChatSource from(RagDocumentRepository.RagHit hit) {
            return new ChatSource(hit.sourceType(), hit.sourceId(), hit.metadata(), hit.similarity());
        }

        public static List<ChatSource> from(List<RagDocumentRepository.RagHit> hits) {
            return hits.stream().map(ChatSource::from).toList();
        }
    }
}
