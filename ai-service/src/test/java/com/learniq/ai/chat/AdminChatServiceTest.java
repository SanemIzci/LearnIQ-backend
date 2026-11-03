package com.learniq.ai.chat;

import com.learniq.ai.chat.AdminChatService;
import com.learniq.ai.dto.AdminChatRequest;
import com.learniq.ai.dto.AdminChatResponse;
import com.learniq.ai.rag.RagDocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminChatService Unit Tests")
class AdminChatServiceTest {

    private static final String TENANT = "akademi_1";

    @Mock
    private RagRetrievalService ragRetrievalService;

    @Mock
    private AdminChatToolService toolService;

    private RagDocumentRepository.RagHit hit(double similarity) {
        return new RagDocumentRepository.RagHit("TEACHER_OBSERVATION", UUID.fromString(
                "00000000-0000-0000-0000-000000000001"),
                "Türev konusunda sorun var; konu tekrarı planlandı.",
                Map.of("classroomName", "12-A", "subjectName", "Matematik"), similarity, "text-embedding-3-small@1536");
    }

    // ── buildUserPrompt (bağlam montajı) ─────────────────────────────────────

    @Test
    @DisplayName("buildUserPrompt - hit'ler numaralı bölüm başlıkları ve içerik olarak yerleşir")
    void buildUserPrompt_includesContext() {
        AdminChatRequest request = new AdminChatRequest("Sınıf nerede zorlanıyor?", UUID.randomUUID(), null, null);

        String prompt = AdminChatService.buildUserPrompt(request, List.of(hit(0.71)));

        assertThat(prompt)
                .contains("Sınıf nerede zorlanıyor?")
                .contains("SEMANTİK BAĞLAM")
                .contains("[BÖLÜM 1 | kaynak=TEACHER_OBSERVATION")
                .contains("sınıf=12-A")
                .contains("ders=Matematik")
                .contains("benzerlik=0.71")
                .contains("Türev konusunda sorun var; konu tekrarı planlandı.");
    }

    @Test
    @DisplayName("buildUserPrompt - bağlam yoksa 'benzer doküman bulunamadı' notu eklenir")
    void buildUserPrompt_emptyContext() {
        AdminChatRequest request = new AdminChatRequest("Genel durum?", null, null, null);

        String prompt = AdminChatService.buildUserPrompt(request, List.of());

        assertThat(prompt).contains("SEMANTİK BAĞLAM: Bu soru için vektör deposunda benzer doküman bulunamadı.");
    }

    // ── chat (ChatClient zinciri + kaynak eşleme) ────────────────────────────

    @Test
    @DisplayName("chat - system+user+3 araç zinciri kurulur, yanıt ve kaynaklar döndürülür")
    void chat_callsLlmWithToolsAndMapsSources() {
        ChatClient chatClient = mock(ChatClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        AdminChatService adminChatService = new AdminChatService(chatClient, ragRetrievalService, toolService);

        when(chatClient.prompt()
                .system(AdminChatService.SYSTEM_PROMPT)
                .user(anyString())
                .function(anyString(), anyString(), any())
                .function(anyString(), anyString(), any())
                .function(anyString(), anyString(), any())
                .function(anyString(), anyString(), any())
                .call()
                .content()).thenReturn("Sınıf türevde zorlanıyor.");

        when(ragRetrievalService.retrieve(eq(TENANT), anyString(), any(), any()))
                .thenReturn(List.of(hit(0.71)));

        AdminChatResponse response = adminChatService.chat(TENANT,
                new AdminChatRequest("Sınıf nerede zorlanıyor?", null, null, null));

        assertThat(response.answer()).isEqualTo("Sınıf türevde zorlanıyor.");
        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().get(0).sourceType()).isEqualTo("TEACHER_OBSERVATION");
        assertThat(response.sources().get(0).similarity()).isEqualTo(0.71);
        assertThat(response.sources().get(0).metadata()).containsEntry("classroomName", "12-A");
    }

    @Test
    @DisplayName("chat - semantik bağlam boşsa da chat tool'larla çalışmaya devam eder")
    void chat_emptyContext_stillCallsLlm() {
        ChatClient chatClient = mock(ChatClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        AdminChatService adminChatService = new AdminChatService(chatClient, ragRetrievalService, toolService);

        when(chatClient.prompt()
                .system(AdminChatService.SYSTEM_PROMPT)
                .user(anyString())
                .function(anyString(), anyString(), any())
                .function(anyString(), anyString(), any())
                .function(anyString(), anyString(), any())
                .function(anyString(), anyString(), any())
                .call()
                .content()).thenReturn("Sayısal fact özeti.");

        when(ragRetrievalService.retrieve(eq(TENANT), anyString(), any(), any()))
                .thenReturn(List.of());

        AdminChatResponse response = adminChatService.chat(TENANT,
                new AdminChatRequest("Ortalama net kaç?", UUID.randomUUID(), null, null));

        assertThat(response.answer()).isEqualTo("Sayısal fact özeti.");
        assertThat(response.sources()).isEmpty();
    }
}
