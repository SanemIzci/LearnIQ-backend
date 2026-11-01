package com.learniq.ai.chat;

import com.learniq.ai.dto.AdminChatRequest;
import com.learniq.ai.dto.AdminChatResponse;
import com.learniq.ai.rag.RagDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Admin RAG chat orkestrasyonu (hibrit):
 * 1. Semantik: kullanıcının sorusu embedding edilip rag_documents'ta aranır,
 *    eşikten geçen dokümanlar bağlam bloğu olarak prompt'a eklenir.
 * 2. Sayısal: getClassSubjectTrend / getAtRiskStudents / compareClassrooms /
 *    getTenantOverview FunctionCallback'leri canlı SQL fact okur (LLM sayı uyduramaz).
 *
 * Tenant izolasyonu: retrieval + her üç tool istekten parametre olarak gelen
 * doğrulanmış kiracı şeması üzerinde çalışır; chat connection'ları paylaşımlı
 * olduğundan isteğe özel kapanışımlarla tool bağlanır.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminChatService {

    static final String SYSTEM_PROMPT = """
            Sen LearnIQ eğitim analitiği platformunun ADMIN asistanısın. Görevin: yöneticinin \
            yazılı sorusunu, sağlanan semantik bağlamı ve araçları kullanarak Türkçe yanıtlamak.

            KURALLAR:
            1. Sayısal soru (net, katılım, trend, öğrenci performansı, sınıf karşılaştırması) \
            görürsen mutlaka ilgili aracı çağır; ASLA sayı uydurma.
            2. Sadece SEMANTİK BAĞLAM bölümünde verilen bilgiyle olmayan şeyleri uydurma; \
            veri yoksa "Veri yok" de.
            3. Araç sonuçlarında verilen öğrenci adları dışında kimlik verisi üretme.
            4. Yanıtını kısa ve net yaz; madde işaretli özet + varsa kaynak atfı kullan.
            """;

    private final ChatClient chatClient;
    private final RagRetrievalService ragRetrievalService;
    private final AdminChatToolService toolService;

    public AdminChatResponse chat(String tenantSchema, AdminChatRequest request) {
        List<RagDocumentRepository.RagHit> context = ragRetrievalService.retrieve(
                tenantSchema, request.message(), request.classroomId(), request.subjectId());

        String userPrompt = buildUserPrompt(request, context);
        log.info("Admin chat: tenant={} bağlam={} adet doküman", tenantSchema, context.size());

        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt)
                .function("getClassSubjectTrend",
                        "Bir sınıfın seçilen denemedeki ders bazlı ortalamalarını döndürür: "
                                + "ortalama net, katılım, önceki denemeye göre delta ve trend yönü. "
                                + "examId verilmezse sınıfın son denemesi kullanılır.",
                        (AdminChatToolService.TrendFilter filter) ->
                                toolService.classSubjectTrends(tenantSchema, filter.classroomId(),
                                        filter.examId(), filter.subjectId()))
                .function("getAtRiskStudents",
                        "Bir sınıfta deneme sonucu ortalama net'i belirtilen eşiğin altında kalan "
                                + "öğrencileri döndürür (zayıf öğrenci listesi). examId verilmezse "
                                + "son deneme, eşik verilmezse varsayılan eşik kullanılır.",
                        (AdminChatToolService.AtRiskFilter filter) ->
                                toolService.atRiskStudents(tenantSchema, filter.classroomId(),
                                        filter.examId(), filter.netThreshold()))
                .function("compareClassrooms",
                        "Birden fazla sınıfı aynı deneme üzerinden karşılaştırır: sınıf ortalama "
                                + "net'i ile en güçlü ve en zayıf derslerini döndürür.",
                        (AdminChatToolService.ComparisonFilter filter) ->
                                toolService.compareClassrooms(tenantSchema, filter.classroomIds(),
                                        filter.examId()))
                .function("getTenantOverview",
                        "Tenant (kurum) geneli özet dashboard döndürür: her sınıfın son denemesindeki "
                                + "ortalama net, katılım oranı, en güçlü ve en zayıf dersleri ile "
                                + "UP/DOWN/STABLE trend sayıları. Genel durum sorularında kullan.",
                        (AdminChatToolService.TenantOverviewFilter filter) ->
                                toolService.getTenantOverview(tenantSchema))
                .call()
                .content();

        return new AdminChatResponse(answer, AdminChatResponse.ChatSource.from(context));
    }

    /** Çekirdek prompt üretimi; test edilebilirliği için package-private static. */
    static String buildUserPrompt(AdminChatRequest request, List<RagDocumentRepository.RagHit> context) {
        StringBuilder sb = new StringBuilder("Yönetici sorusu: ").append(request.message()).append("\n\n");

        if (context.isEmpty()) {
            sb.append("SEMANTİK BAĞLAM: Bu soru için vektör deposunda benzer doküman bulunamadı.\n");
        } else {
            sb.append("SEMANTİK BAĞLAM (benzerlik en yüksekten başlayarak):\n");
            int i = 1;
            for (RagDocumentRepository.RagHit hit : context) {
                Map<String, Object> metadata = hit.metadata();
                sb.append("\n[BÖLÜM ").append(i++)
                        .append(" | kaynak=").append(hit.sourceType())
                        .append(" | sınıf=").append(metadata.getOrDefault("classroomName", "-"))
                        .append(" | ders=").append(metadata.getOrDefault("subjectName", "-"));

                if (metadata.get("examTitle") != null) {
                    sb.append(" | deneme=").append(metadata.get("examTitle"));
                }
                if (metadata.get("observedAt") != null) {
                    sb.append(" | gözlemTarihi=").append(metadata.get("observedAt"));
                }
                sb.append(" | benzerlik=")
                        .append(String.format(java.util.Locale.ROOT, "%.2f", hit.similarity()));
                sb.append("]\n").append(hit.content()).append("\n");
            }
        }
        return sb.toString();
    }
}
