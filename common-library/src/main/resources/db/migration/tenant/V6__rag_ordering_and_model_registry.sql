-- RAG sıralama/atomik yazım + embedding model registry.
-- Sıralama: aggregateVersion (monotonik) + tombstone (soft-delete).
-- Model registry: embedding_model + embedding_dims; retrieval imza filtresi kullanır.

ALTER TABLE rag_documents ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE rag_documents ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
ALTER TABLE rag_documents ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(120);
ALTER TABLE rag_documents ADD COLUMN IF NOT EXISTS embedding_dims INT;
ALTER TABLE rag_documents ADD COLUMN IF NOT EXISTS exam_id UUID;

-- Aynı aggregate'in (classroom × exam) birden fazla fact dokümanı olabilir;
-- exam silme tombstone'u bu kolon üzerinden yapılır.
CREATE INDEX IF NOT EXISTS idx_rag_documents_exam ON rag_documents(source_type, exam_id);

-- Yalnız canlı (silinmemiş) dokümanlar üzerinden arama.
CREATE INDEX IF NOT EXISTS idx_rag_documents_live
    ON rag_documents(source_type, source_id) WHERE deleted_at IS NULL;

-- Producer tarafı aggregate version kolonları (event zarfı için).
ALTER TABLE teacher_observations ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE class_subject_trends ADD COLUMN IF NOT EXISTS calc_version BIGINT NOT NULL DEFAULT 1;
