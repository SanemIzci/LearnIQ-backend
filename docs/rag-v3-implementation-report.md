# RAG Pipeline v3 Uygulama Raporu (2026-11-03)

## 1. Kapsam
Referans: `docs/rag-pipeline-review.md` (v3). Faz 1–2 çekirdeği + kısmi faz 3–5 tamamlandı. Değişiklikler commit edilmemiş, çalışma dizininde bekliyor.

## 2. common-library (ortak katman)
- **Yeni `RagEvent.java`**: schema v2 event zarfı helper (`eventId, schemaVersion=2, eventType, tenantId, aggregateId, aggregateVersion, occurredAt, payload`); `payloadOf`/`aggregateVersionOf` ile legacy (zarfsız) mesajlar version=0 kabul edilir.
- **`V6__rag_ordering_and_model_registry.sql`**: `rag_documents` + `version/deleted_at/embedding_model/embedding_dims/exam_id` kolonları, `idx_rag_documents_exam` ve partial `idx_rag_documents_live` index'leri; `teacher_observations.version`; `class_subject_trends.calc_version`.
- **`V7__ensure_rag_embedding_column.sql`**: pgvector sonradan aktifleşen şemalar için koşullu embedding kolonu + HNSW; `table_schema = current_schema()` filtresi eklendi (çok-tenant'ta yanlış şemadan kontrol hatası giderildi).

## 3. exam-service (producer tarafı)
- **TeacherObservation**: `@Version version`; create/update `saveAndFlush` + zarf event (partition key = observationId); delete tombstone event (version+1).
- **ClassSubjectTrendCalculator**: `nextCalcVersion(classroom, exam)` = MAX(calc_version)+1; upsert `calc_version` yazar; `CLASS_ANALYSIS_COMPLETED` zarfı, key = `classroomId:examId`.
- **ExamService.deleteExam**: `EXAM_DELETED` reconciliation zarfı (version = wall-clock, terminal aggregate), key `exam:<id>`, topic `class-analysis-topic`.
- **KafkaAfterCommit**: key'li send overload; keysiz yol eskisi gibi 2-arg send (regresyon yok).

## 4. ai-service (consumer/retrieval/ops)
- **RagDocumentRepository** (rewrite): `DocState` (hash+version+tombstone+model); koşullu UPSERT (`INSERT ... AS rd ... WHERE rd.version <= EXCLUDED.version`, dağıtık-safe, lock yok); `tombstone/tombstoneByClassroom/tombstoneByExam` (fiziksel DELETE yok, `version = GREATEST(version,?)+1`); `search` (`deleted_at IS NULL` + opsiyonel `embedding_model IN (...)` + pre-filter + topK); backfill (`findBackfillCandidates`/`updateEmbedding`, version korunur), `countLive`.
- **IngestionService** (rewrite): embedding TX dışında; hash+model idempotency; erken stale kontrolü (tombstone dahil); `deleteExam/deleteClassroom/deleteObservation` version'lı; observation fact okumasında `tags::text` (PGobject cast hatası); legacy event uyumu.
- **RagIngestConsumer**: zarf parse (nested payload), `EXAM_DELETED`/`CLASSROOM_DELETED` yönlendirme; bozuk JSON ve eksik alan → `IllegalArgumentException` (kalıcı, retry'sız DLT).
- **RagRetrievalService**: `allowed-model-sigs` filtresi (boş = filtre yok); pgvector yok / provider hatası → boş bağlam (chat tool-only devam eder); eşik filtresi.
- **EmbeddingClientConfig**: chat'ten bağımsız `@Primary` `EmbeddingModel` bean (`rag.embedding.*`).
- **RagMetrics**: embedded / hashSkipped / staleRejected / retries / dlt / retrievalEmpty / embed latency (Micrometer).
- **RagKafkaErrorHandlerConfig**: 3 retry üstel backoff; `JsonProcessing/IllegalArgument/DataIntegrity` non-retryable; `DeadLetterPublishingRecoverer` (`<topic>.DLT`, partition korunur) + DLT meter; concurrency=2; ayrı JsonSerializer'lı `dltKafkaTemplate`; `ConsumerFactory<Object,Object>` generic uyumu.
- **RagBackfillService + RagAdminController**: `POST /api/v1/admin/ai/rag/backfill` (ADMIN/SUPER_ADMIN, X-Tenant-Id, batchSize). DLT replay endpoint bilinçli olarak yok (RTL-1).
- **application.yml/pom**: `rag.embedding.*`, `rag.chat.allowed-model-sigs`, `rag.backfill.batch-size`; actuator dependency + exposure; testcontainers + failsafe.

## 5. Kök pom
- `testcontainers.version=1.21.3` ve `argLine`'a `-Dapi.version=1.44` (Docker Engine 29 minimum API 1.44 uyumu için gereken workaround).

## 6. Testler
- Güncellenen: IngestionServiceTest, RagDocumentRepositorySearchTest (+allowedModels), RagRetrievalServiceTest, AdminChatServiceTest (RagHit 6-arg), RagIngestConsumerTest (+zarf v2 ve EXAM_DELETED testleri), TeacherObservationServiceTest (saveAndFlush + zarf + key), ClassSubjectTrendCalculatorTest (calc_version + varargs matcher düzeltmeleri).
- **Yeni `RagPipelineOrderingIT`** (Testcontainers pgvector/pg16 + gerçek tenant Flyway migration): IT-1 bayat upsert reddedilir; IT-2 duplicate idempotent; IT-3 tombstone bayat update'i diriltmez; IT-4 üst version meşru diriltir; IT-5 exam silmesi türetilenleri tombstone'lar; IT-6 arama tombstone/model-dışı dokümanları dışlar; IT-7 backfill version'u korur; IT-8 hash-skip + silme sonrası bayat event yok sayılır.
- **Sonuç**: `mvn verify` BUILD SUCCESS — **318 test** (gateway 11, auth 47, exam 189, ai 63 unit + 8 IT). Test sürecinde bulunan ve giderilen gerçek hatalar: PGobject→String cast, V7 şema filtresi eksikliği, ConsumerFactory generic uyumsuzluğu.

## 7. Kalan işler
- Kafka-container'lı uçtan uca IT (producer→consumer→DLT/retry senaryoları).
- `CLASSROOM_DELETED` producer (sınıf silme endpoint'i yok; consumer hazır).
- DLT replay endpoint (RTL-1 onayına kadar yok) + alerting eşikleri/runbook.
- Plan'daki 5 açık soru henüz cevaplanmadı; makul varsayılanlarla ilerlendi.

## 8. Deployment notları
- **Sıra: migration (V6/V7) → ai-service (consumer-first) → exam-service.** Yeni consumer hem v2 zarfı hem legacy (zarfsız, version=0) mesajları işler; eski consumer ise v2 zarfı okuyamaz (alanlar `payload` altında kaldığı için `examId`/`observationId` null olur → DLT). Bu nedenle producer-first deployment ingestion kaybı yaratır; consumer-first zorunludur.
- Yeni env'ler: `RAG_EMBEDDING_API_KEY/_BASE_URL/_MODEL/_DIMENSIONS/_MODEL_SIG`, `RAG_ALLOWED_MODEL_SIGS`, `RAG_BACKFILL_BATCH_SIZE`.
- Geriye dönük uyum: zarfsız mesaj kabul (version 0), boş `allowed-model-sigs` → model filtresi yok (yalnız dev/pilot; prod'da startup fail-fast).
- Legacy (version=0) kaldırma kriteri: tüm ortamlarda tüm producer'lar v2'ye geçtikten sonra en az 2× rebalance/quota periyodu boyunca legacy oranı 0 olmalı; sonra flat mesaj desteği kaldırılabilir.
