# RAG Pipeline — Production Readiness Review (v3)

**Durum:** v3 — istenen teknik düzeltmeler entegre; v2'de doğrulanan tüm bulgular korunur.

---

## A. Current Architecture Assessment

Doğrulanmış akış:

```
exam-service (producer):                     ai-service (consumer):
ClassSubjectTrendCalculator.calculate()      RagIngestConsumer (2 topics)
TeacherObservationService CRUD                  │ afterCommit publishing (KafkaAfterCommit)
  └─ KafkaAfterCommit.send(...)                 ├─ class-analysis-topic (CLASS_ANALYSIS_COMPLETED)
     (key = null, StringSerializer)             ├─ teacher-observations-topic (CREATED/UPDATED/DELETED)
     topics: class-analysis-topic,              ├─ concurrency=2, ragListenerContainerFactory
             teacher-observations-topic         ├─ DefaultErrorHandler (3 retries, exp backoff 1s→8s)
                                                │  not-retryable: JsonProcessing, IAE, DataIntegrityViolation
                                                └─ after 3 failures → <topic>.DLT (JsonSerializer template)
IngestionService (@Transactional):
  switchTo(schema) → read facts from tenant schema (qualified SQL)
  → FactDocumentFactory (CLASS_ANALYSIS_FACT per classroom×subject×exam)
  → sha256(content) vs findHashBySource → skip if same
  → embeddingModel.embed(content) → upsert ON CONFLICT(source_type, source_id)
```

**Retrieval:** `AdminChatController` (role header fail-closed) → `RagRetrievalService` → embed question → `RagDocumentRepository.search` → schema-qualified SQL with cosine `1 - (embedding <=> ?::vector)`, optional classroom/subject pre-filter, LIMIT 5 → client-side threshold 0.30 → prompt context + 4 fixed tool callbacks by `AdminChatService`.

**Doğrulanmış mimari gerçekler:**
- Sayısal chunking yok — "chunk"lar atomik fact dokümanları (1 exam × classroom × subject, veya 1 teacher observation). Fact-atomic tasarım; window/overlap yok.
- Tenant şeması: her SQL birleştirmesinden önce `TenantSchemaValidator.requireValid` regex doğrulaması — `RagDocumentRepository`, `IngestionService`, `AdminChatToolService`'te doğrulandı. Her tool `switchTo()` (doğrular) çağırır, sonra SQL'i ham string ile kurar; bugün sıra sayesinde çalışır — kırılgan.
- `rag_documents`: `UNIQUE(source_type, source_id)` + content_hash SHA-256; tenant şeması V5 içindeki koşullu `DO` bloğunda HNSW + `vector_cosine_ops`.
- Retry/DLT: 3 exponential retry; ayrı JsonSerializer template ile `<topic>.DLT`.
- Chat tarafı: embedding `text-embedding-3-small`, chat ile aynı OpenRouter base-url'i üzerinden (tek `spring.ai.openai.base-url`). OpenRouter embeddings endpoint'i sunmuyor; yml'de belirsizlik itiraf eden satır içi yorum var.
- Hiçbir yerde metrik/trace/dashboard yok; yalnız log.
- DLT: `<topic>.DLT`'ye publish — replay/consumer aracı yok.

---

## B. Findings — doğrulama sınıflandırması ile

Her bulgu **sınıflandırma** taşır: **[Confirmed defect]** doğrudan kod/konfig hatası · **[Confirmed weakness]** mimari zayıflık · **[Conditional risk]** gerçek yolu kanıtlanmalı · **[Recommendation]** iyileştirme.

| ID | Bulgu | Sınıf | Severity | Evidence | Impact | Confidence |
|---|---|---|---|---|---|---|
| F-01 | Event sıralama/version yok; key=null producer + concurrency=2 → aynı kaynak UPDATE/UPDATE, UPDATE/DELETE yarışı; hash idempotency "yeni mi" sorusunu cevaplamaz; DELETED sonrası geç UPDATE dokümanı yeniden oluşturur | Confirmed defect | P1 | `RagIngestConsumer`, `KafkaAfterCommit.send` (key null), `RagKafkaErrorHandlerConfig`, `IngestionService.ingestDoc` | Bayat/hayalet doküman admin'e ulaşır; ödenmiş re-embed | Confirmed (kod yolu kanıtlı; yarış tetiklenmesi koşullu ama gerçekçi) |
| F-02 | Upstream silme (exam/classroom cascade) RAG'e event üretmez → hayalet dokümanlar kalıcı görünür | Confirmed defect (kapsama açığı) | P1 | `V1:27,51`, `V5:26,42`, 4 mevcut event tipi | Yanlış/tarihi LLM yanıtı; retention açığı | Confirmed |
| F-03 | Embedding HTTP çağrısı `@Transactional` içinde; pool(5) + 2 consumer; embed başarısızlığında rollback → tekrar embed (maliyet) | Confirmed weakness | P1 | `IngestionService:41,95,149-160` | Bağlantı açlığı; ölçeklenebilirlik | Confirmed |
| F-04 | Chat ve embedding aynı base-url/api-key'i paylaşır (OpenRouter); yml'de ayrı embedding property yok | Confirmed defect *(config-level)* | P1 | `ai-service/application.yml:29-42` | "OpenRouter embeddings'i desteklemiyor" iddiası runtime'da kanıtlanmalı; config defect ile kesin runtime failure ayrı raporlanır | Config: Confirmed · Runtime: To-verify (HTTP IT Faz 1) |
| F-05 | Dokümanda embedding model kimliği/dimension yok; provider/model değişimi: aynı dim → sessiz kalite bozulması; farklı dim → `vector(1536)` yazım hatası → DLT; re-embed yolu yok | Confirmed weakness | P1 | `V5:58-85`, `RagDocumentRepository.upsert`, yml | Gelecek provider geçişi kırıcı | Confirmed |
| F-06 | Provider hatasında chat embed'i exception'ı yükseltir → 500 (pgvector-yok guard'ı var, provider-hata guard'ı yok) | Confirmed weakness | P2 | `RagRetrievalService:48-52` | Provider kesintisinde chat tamamen düşer, tool yolu çalışırken | Confirmed |
| F-07 | pgvector sonradan aktifleştirilirse V5 DO bloğu koşmaz; embedding kolonu/olası sonuç farkı kalıcı | Confirmed weakness | P2 | `V5:79-88`, `embeddingColumnExists`, docker-compose init notu | Tenant'lar arası yetenek tutarsızlığı | Confirmed |
| F-08 | Metrik/trace yok (yalnız log.info) | Confirmed weakness | P2 | tüm rag/chat sınıfları | Operasyonel geri bildirim yok | Confirmed |
| F-09 | Gözlem içeriği prompt'a serbest metin olarak girer; enjeksiyon LLM yanıtını yönlendirebilir; araç seçimi sabit closure'larla bağlandığından tool escalation yolu bulunamadı | Conditional risk (düşük istismar edilebilirlik) | P2 | `buildUserPrompt:96-115` | Yanıt manipülasyonu; arayan zaten ADMIN | Conditional (istismar yolu kanıtlanmadı; yüzey doğrulandı) |
| F-10 | DLT tüketici/izleme yok; provider kesintisi >3 retry → DLT'ye düşer ve beklemede kalır → ingestion gap (veri kaybı değil; recovery gerektirir) | Confirmed weakness | P2 | `RagKafkaErrorHandlerConfig` | Korpus boşluğu; fark edilmez | Confirmed |
| F-11 | `trusted.packages="*"`, StringSerializer+JsonDeserializer konvansiyon bağımlılığı; event şema evrim kuralı yok | Recommendation | P3 | `application.yml`, `toEventMap` | Gelecek kırılganlık | Confirmed (fragility only) |
| F-12 | `SET search_path` vestijiyel (30 çağrı yeri şema-nitelikli); kaldırılması zorunlu refactoring değildir — risk, gelecekte switchTo'suz SQL yazımı | Recommendation | P3 | `TenantSchemaService` vb. | Kod kırılganlığı | Confirmed (cosmetic) |
| F-13 | Küçük: toBigDecimal double dönüşümü, sınırsız compareClassrooms listesi, 6 ondalık vector literal, HNSW varsayılanları benchmarksız | Recommendation | P3 | `RagDocumentRepository:195-202`, `AdminChatToolService:175-205` | Kozmetik | Confirmed (minor) |

**Tenant izolasyon çekirdek kontrolü:** event `tenantId` tüm SQL birleştirmeden önce regex doğrulanır; tüm okuma/yazma şema-nitelikli; cross-tenant yolu bulunamadı. **Tool yetkilendirmesi server-side'dır ve korunur** (tenantSchema servis parametresidir; LLM'in seçtiği classroom filtreleri yalnız o şema içinde etkilidir — LLM'e ayrı yetki verilmez).

---

## C. Revize Çözüm Mimarisi (v3 kararları)

### C1. Event zarfı ve aggregate sınırları (F-01)

- Zarf alanları: `eventId` (UUID, dedupe/telemetri), `eventType`, `schemaVersion`, `tenantId`, `aggregateId`, `aggregateVersion`, `occurredAt`, `payload`. **Sıralama yalnız `aggregateVersion` ile; `occurredAt` yalnız telemetri** (duvar saati sıralama amaçlı kullanılmaz).
- Aggregate sınırları ayrı tanımlıdır:
  - **TeacherObservation**: aggregate = (tenant, observationId); yaratma=1, her update +1 (`UPDATE ... SET version=version+1 RETURNING` producer TX'i içinde); DELETE eventi kendi version'unu taşır. Birebir fact doküman.
  - **ClassAnalysis**: aggregate = (tenant, classroomId, examId); bir event **birden fazla fact doküman üretebilir** (ders başına 1). `class_subject_trends.calc_version` hesaplama TX'inde aggregate düzeyinde artırılır ve tüm fact'ler **aynı aggregateVersion'ı** paylaşır (aynı TX'te yazıldıkları için tutarlıdır). Fact başına source_id = `deterministicUuid(classroomId, subjectId, examId)`.
- **Kafka partition key = aggregateId** → aynı aggregate'in tüm eventleri tek partition'da sıralı; farklı aggregate'ler paralel işlenebilir.

### C2. Atomik version-aware UPSERT / tombstone (F-01)

- `rag_documents` yeni kolonlar: `version BIGINT`, `deleted_at TIMESTAMPTZ NULL`, `embedding_model TEXT`, `embedding_dims INT`.
- UPSERT (tek statement, atomik):

  ```sql
  INSERT ... ON CONFLICT (source_type, source_id) DO UPDATE SET
    ..., deleted_at = NULL, version = EXCLUDED.version
  WHERE rag_documents.version < EXCLUDED.version
  ```

  → bayat yazım **DB tarafından** reddedilir; iki consumer instance yarışsa bile kazanan en yüksek version'dur. **ReentrantLock ana çözümden çıkarıldı.**
- DELETE **tombstone** (fiziksel delete yasak):

  ```sql
  UPDATE rag_documents SET deleted_at = NOW()
  WHERE source_type = ? AND source_id = ? AND version < ?
  ```

  → DELETE sonrası gelen bayat UPDATE ekleme yapamaz (satır durur, version fence korunur). Retrieval/tool okumaları `deleted_at IS NULL` filtreler. Purge job'ı ayrı, sonradan (opsiyonel).

### C3. Transaction sınırları (F-03)

Akış: (1) fact oku (TX yok), (2) hash kontrolü, (3) `embed()` **TX dışında**, (4) **atomik version-aware UPSERT/DELETE** (DB koşulu "still current?" doğrulamasıdır; Java-side karşılaştırmaya güvenilmez), (5) hata → hiçbir kısmi yazım yok, Kafka retry/DLT. `@Transactional` ingest metotlarından kalkar.

### C4. Embedding provider (F-04)

Bağımsız `EmbeddingModel` bean (Spring AI sürümünün property desteğine **varsayım yok**: ayrı `OpenAiApi`/`OpenAiEmbeddingModel` bean, kendi base-url/api-key/model'i; chat `ChatClient` dokunulmaz). **OpenRouter iddiası MockWebServer-over-the-wire IT ile doğrulanır**: gerçek socket üzerinden istek embedding base-url'ine gider; config defect (paylaşılmış property) ile runtime failure (HTTP 404/401) ayrımı raporda korunur. Provider erişilebilirliği varsayılmaz.

### C5. Model versioning + dimension migration (F-05)

- Satır başına `embedding_model` = `"<model>@<dims>"` imzası ve `embedding_dims`.
- **vector(1536) ve farklı dimension senaryosu:** pgvector'da typmod kolon başınadır; dimension değişimi tek kolonda yönlendirilemez → geçiş için (i) `ALTER ... USING` ile typmod değişimi (kısıtlı), (ii) **ikinci kolon** `embedding_next vector(M)` üzerine yazım, ardından kolon swap (önerilen). Plan: V6 mevcut 1536'yı korur; dimension değişikliği gerektiğinde ayrı V(n+) migration swap ile; kod `embedding_dims` tabanlı kolon çözümü seçer.
- **Re-embed tamamlanmadan eski dokümanların visibility dışına alınması veri boşluğu yaratır** → geçiş stratejisi: (1) yeni imza allowed listesine **eklenir** (eski dokümanlar görünür kalır), (2) backfill batch job (`rag.backfill.*`, progress metriği, per-batch commit) eski imzaları yeniden embed eder, (3) coverage metriği (`rag.corpus.signature.coverage`) %100'e ulaşmadan eski imza allowed listesinden çıkarılmaz; geri alınabilir (property revert). R3 backfill ve bu kriterler tutarlıdır.

### C6. DLT (F-10)

**Message loss olarak tanımlanmaz**: ingestion gap + recovery problemidir. İlk aşama: (1) DLT metrics+alerting (rate per topic; header'da original topic/partition/offset/exception — `DeadLetterPublishingRecoverer` header config), (2) kontrollü replay **prosedürü** (runbook: gap tespiti → gap window itirafı → replay). **Admin replay endpoint'i ancak güvenlik, idempotency (version-fence zaten sağlar) ve offset yönetimi netleştikten sonra** önerilir (RTL-1 açık soru; offset yaklaşımı: replay = yeni kayıt yayını, consumer offset geri sarma yok, original key korunur — bunlar onaylanırsa endpoint Faz 4 opsiyonel adımıdır).

### C7. Observability (Faz 2 ile birlikte)

`rag.ingest.embedded`, `rag.ingest.hash.skipped`, `rag.ingest.stale.rejected{aggregate}`, `rag.ingest.embed.latency`, `rag.ingest.retries`, `rag.ingest.dlt`, `rag.retrieval.hits/empty/model.mismatch`, `rag.corpus.signature.coverage`.

---

## D. Dependency Analysis

```
C4 (provider, Faz 1)           bağımsız — ilk başlar
C1+C2+C3 (envelope+atomik+TX)  Faz 2: aynı IngestionService/consumer dokunuşu → birlikte; C4 gerektirir (embed ulaşılabilir olmalı)
V6 migration                   Faz 2 için tek migration: rag_documents(version, deleted_at, embedding_model, embedding_dims)
                               + teacher_observations.version + class_subject_trends.calc_version → C5 kolonları buradan hazır
C7 metrics                     Faz 2 kabul kriteri
Reconciliation (Faz 3)         Faz 2 zarfı + tombstone gerektirir
DLT monitoring (Faz 4)         Faz 1/2 sonrası; replay endpoint → RTL-1'e bağlı, opsiyonel
Backfill+coverage (Faz 4)      Faz 2 kolonları + Faz 1 provider
Hardening (Faz 5)              Faz 4 metrikleri sonrası; prompt fence tek başına güvenlik çözümü DEĞİL
                               (defense-in-depth; tool auth server-side korunur)
```

---

## E. Implementation Phases

### Faz 1 — Provider ayrımı + HTTP doğrulama (F-04)

1. **Objective:** Chat ve embedding provider bağımsız; default deployment ingest edebilir; provider varsayımları gerçek HTTP testiyle kanıtlanır.
2. **Problems:** F-04.
3. **Files:** `ai-service/src/main/resources/application.yml`, yeni `EmbeddingClientConfig` (bağımsız `EmbeddingModel` bean), `EmbeddingProviderHttpIT`.
4. **Approach:** ayrı base-url/api-key/model; feature flag; chat davranışı değişmez; Spring AI property adları varsayılmaz.
5. **Dependencies:** yok.
6. **Regressions:** bean çakışması riski (tek `EmbeddingModel`; test ile sabitlenir).
7. **Tests:** IT-1 (gerçek socket): embed isteği embedding URL'ine gider; chat URL ayrı; 401/500/timeout sınıflandırması; dimension doğrulama; **OpenRouter runtime iddiasının doğrulanması** (sonuç rapora not edilir).
8. **Acceptance:**
   - Wire üzerinde ayrı endpoint kanıtlandı.
   - Hatalı provider → deterministik başarısızlık; hiçbir DB yazımı yok.
   - Config defect adreslendi; runtime failure ayrımı raporlandı.

### Faz 2 — Envelope + aggregateVersion + atomik yazım + TX + metrikler (F-01, F-03, F-08)

1. **Objective:** Bayat/out-of-order/duplicate event'ler dokümanı bozamaz veya diriltemez; embedding DB transaction dışında; temel metrikler mevcut.
2. **Problems:** F-01, F-03, F-08.
3. **Files:** `common-library/src/main/resources/db/migration/tenant/V6__rag_ordering.sql`, exam-service producer'lar (`KafkaAfterCommit.send(topic, key, envelope)`; key=aggregateId), `RagIngestConsumer` (zarf doğrulama: IAE→DLT), `IngestionService` (TX kaldırma + C3 akışı), `RagDocumentRepository` (C2 conditional UPSERT/tombstone, `deleted_at IS NULL` okumalar), `RagRetrievalService`, metrik konfigürasyonu.
4. **Approach:** zarf alanları (C1); producer TX'inde version üretimi; partition key=aggregateId; conditional UPSERT/tombstone; `@Transactional` kaldırma; Micrometer (C7).
5. **Dependencies:** Faz 1.
6. **Regressions:**
   - Eski zarfsız mesajlar: version alanı yoksa **backward-compatible kabul** (version=NULL → bugünkü davranış, deprecated log).
   - Deployment sırası: migration → producer → consumer (RTL-5).
   - Mevcut dokümanlar version=0 default → ilk event kabul edilir.
7. **Tests (ayrı integration senaryoları):** IT-2 out-of-order, IT-3 concurrent UPDATE/DELETE, IT-4 duplicate, IT-5 tenant isolation, IT-8 rollback/no-partial + TX-dışı embed kanıtı; metrik varlık testleri.
8. **Acceptance:**
   - IT-2/3/4/5/8 yeşil (gerçek Kafka + Postgres/pgvector).
   - Stale reddi metrikte görünür.
   - Embed çağrısı açık TX içinde değil (enstrümantasyonla kanıt).
   - Duplicate → tek embed, tek satır.

### Faz 3 — Data Lifecycle Reconciliation (F-02)

1. **Objective:** Upstream silmeler (exam/classroom/observation) ilgili RAG dokümanlarını tombstone'lar.
2. **Problems:** F-02.
3. **Files:** exam-service silme yolları (`EXAM_DELETED`, `CLASSROOM_DELETED`; classroom cascade → gözlem başına/aggregate başına tombstone eventleri), `RagIngestConsumer` yönlendirme, `IngestionService.deleteByAggregate`, `RagDocumentRepository` (aggregate bazlı tombstone).
4. **Approach:** `KafkaAfterCommit` ile additive yayınlama; tombstone C2 kurallarıyla; classroom silme sessiz DB cascade'ine ek olarak event üretir.
5. **Dependencies:** Faz 2 (zarf + tombstone).
6. **Regressions:** bilinmeyen event tipleri zaten skip (güvenli); zarf v2 şemasına bağımlılık Faz 2'den.
7. **Tests:** IT-6: exam silme → class-analysis tombstone'ları; classroom silme → observation tombstone'ları; bayat DELETE yeni dokümanı silemez.
8. **Acceptance:**
   - Silinen upstream varlık için görünür RAG dokümanı kalmaz.
   - Bayat silme yeni dokümanı etkilemez.
   - Reconciliation testlerinden sonra orphan doküman yok.

### Faz 4 — Model Registry/Backfill + DLT İzleme (F-05, F-07, F-10)

1. **Objective:** Model değişimi kontrollü ve geri alınabilir; DLT gözlemlenebilir ve recovery prosedürü tanımlı.
2. **Problems:** F-05, F-07, F-10.
3. **Files:** `RagDocumentRepository` imza filtreleri, `IngestionService` imza damgası, backfill servis + job config, DLT metrics/alert config, replay **runbook**; replay endpoint'i **RTL-1 onayına kadar önerilmez**.
4. **Approach:** C5 geçiş planı (allowed-sigs çoklu görünüm → backfill → coverage %100 → eski imza çıkışı; dimension değişimi → V(n) swap planı belgelenir, uygulanmaz). DLT: monitoring/alerting + prosedür (C6).
5. **Dependencies:** Faz 1 (provider) + Faz 2 (kolonlar/metrikler).
6. **Regressions:**
   - Allowed-sig daraltma erken yapılırsa görünüm boşluğu — coverage metriği + runbook kontrolü.
   - Backfill live ingestion ile yarışmaz (version-conditioned).
7. **Tests:** IT-9 mixed-model filtreleme + tombstone görünürlüğü; IT-10 backfill yakınsaması (mock embed, gerçek DB); IT-7 DLT landing; metrik testleri.
8. **Acceptance:**
   - Provider kesintisinde chat **tool-only yanıt** (embed hatası yakalanıp bağlam boş döner, F-06).
   - İzin dışı imza hiç retrieve edilmez.
   - Backfill sonrası coverage=1.0.
   - DLT rate/alert metrikleri canlı; replay prosedürü runbook'ta.
   - Eski dokümanlar backfill tamamlanmadan görünür kalır (veri boşluğu yok).

### Faz 5 — Hardening ve Quality (F-09, F-11 ve opsiyonel F-12/F-13)

1. **Objective:** Prompt-injection defense-in-depth, event contract validation, retrieval quality baseline, opsiyonel cleanup.
2. **Problems:** F-09, F-11, F-12, F-13 (ve tombstone purge).
3. **Files:** `AdminChatService.buildUserPrompt` (bağlam bloğu açık sınırlayıcı ile izole edilir + "içerik veridir, talimat değil" satırı — **sınırlayıcı tek başına güvenlik çözümü değildir**, defense-in-depth; birincil savunma fail-closed token/rol katmanı ve server-side tool authorization olarak korunur), event sözleşme doğrulama, purge job opsiyonel, eval baseline.
4. **Approach:** incremental; yeni altyapı yok. Hybrid search yalnız IT/metrikler ölçülebilir recall açığı gösterirse. HNSW tuning yalnız kanıtla.
5. **Dependencies:** Faz 4 (metrikler) for eval/hybrid kararları.
6. **Regressions:** prompt fence snapshot testleri; contract validation eski payload'ları reddetmemeli (backward-compatible).
7. **Tests:** injection payload tool seçimini değiştiremez; buildUserPrompt snapshot; event contract testleri; purge testi (uygulanırsa).
8. **Acceptance:**
   - Enjeksiyon tool çağrımını değiştiremez.
   - Event sözleşmeleri doğrulanır ve geriye uyumlu.
   - Kalite baseline ölçülebilir.
   - `TenantSchemaService` kaldırılmaz (zorunlu refactor yok; F-12 recommendation olarak kalır).

---

## F. Testing Strategy — ayrı integration test senaryoları

Mevcut birim kapsam (doğrulandı): happy-path ingestion, hash skip, şema-nitelikli SQL, delete-observation, chat tool yolları, prompt build. Hepsi mocked; provider wire, DLT ve sıra-hassas akışlar test edilmemiş.

Testcontainers (Kafka + pgvector'lı PostgreSQL) bu iş için kabul edildi.

| Senaryo | Amaç | Faz | Durum |
|---|---|---|---|
| IT-1 Provider HTTP (MockWebServer) | ayrı endpoint + hata sınıflandırma + OpenRouter iddiası doğrulaması | 1 | yok |
| IT-2 Out-of-order | bayat UPDATE yeniyi ezmez | 2 | yok |
| IT-3 Concurrent UPDATE/DELETE | gerçek yarışta deterministik final; resurrection yok | 2 | yok |
| IT-4 Duplicate event | tek embed, tek satır | 2 | partial (hash-skip unit) |
| IT-5 Tenant isolation | paylaşımlı havuzda çapraz-tenant sızıntı yok | 2 | yok |
| IT-6 Reconciliation | silme → tombstone | 3 | yok |
| IT-7 DLT landing | kalıcı hata `<topic>.DLT`'de | 4 | yok |
| IT-8 Rollback/no-partial + TX-dışı embed | hata → DB durumu değişmez | 2 | yok |
| IT-9 Retrieval authz + tombstone/mixed-model görünürlük | filtreler çapraz-tenant geçemez; silinmiş/izin-dışı imza görünmez | 2/4 | yok |
| IT-10 Backfill yakınsama | coverage=1.0 | 4 | yok |

Birim testler (mevcut, mocked) korunur; Testcontainers IT-2..IT-10 için kullanılır; exam-service tarafı zarf/key birim testleri.

---

## G. Production Readiness Verdict

- **Production-ready mi?** RAG alt sistemi **değil** (306 test yeşil fakat wire/DLT/order/deletion yolları hiç çalıştırılmamış).
- **Release blocker'lar:** Faz 1 (F-04 config; runtime kanıtlanacak) + Faz 2 (F-01, F-03) + Faz 3 (F-02).
- **GA öncesi tercih, haftalara bölünebilir:** Faz 4 (kolonlar Faz 2'de hazır; backfill/replay sonra).
- **Ertelenebilir:** Faz 5, purge, hybrid search, HNSW tuning, eval.
- **Blocker olmayanlar:** F-06 (Faz 4'te çözülür), F-07, F-09 (conditional, ADMIN-only), F-11–F-13 (öneri).

---

## H. Revision Log

**v1 → v2:** 7 revizyon (aggregateVersion, DB-conditional, provider ayrımı, model registry, DLT, metrics, Testcontainers).

**v2 → v3:**
1. Zarf alanları netleştirildi; aggregate sınırları ayrı tanımlandı; class-analysis'ın çoklu-fact doküman modeli eklendi.
2. `ReentrantLock` tamamen çıkarıldı; DB-side version-aware atomik yazım tek otorite.
3. Spring AI property varsayımı yok — bağımsız `EmbeddingModel` bean; OpenRouter iddiası gerçek HTTP IT ile doğrulanacak; config defect ↔ runtime failure ayrımı korundu.
4. Model + dimension kalıcılaştırıldı; `vector(1536)` ve farklı-dimension migration senaryosu açıklandı; coverage-driven geçiş ile veri boşluğu yönetildi.
5. DLT "message loss" değil "ingestion gap/recovery" olarak yeniden tanımlandı; replay endpoint'i güvenlik/idempotency/offset netleşene kadar ertelendi (RTL-1).
6. R3 acceptance ↔ backfill tutarlılığı sağlandı; prompt injection delimiter tek başına çözüm olarak sunulmadı; server-side tool authorization korundu; `TenantSchemaService` kaldırma zorunlu refactor olmaktan çıkarıldı; ayrı integration test senaryoları tanımlandı; bulgu sınıf ayrımı (defect/weakness/conditional/recommendation) netleştirildi.

---

## I. Open Questions

1. Tombstone + gelecekteki purge job onayı?
2. `aggregateVersion` üretimi: producer TX sürüm kolonları (V6) — onay?
3. DLT replay yetkisi (SUPER_ADMIN header deseni) — Faz 4 endpoint'i için RTL-1: onay/ertele?
4. IT-1'de gerçek provider smoke testi CI-dışı env-gated çalışsın mı?
5. Deployment sırası (migration → producer → consumer; NULL-version backward compatibility) — onay?
