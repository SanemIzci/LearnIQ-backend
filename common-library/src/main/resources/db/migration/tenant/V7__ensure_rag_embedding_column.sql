-- pgvector daha sonra etkinleştirilen mevcut tenant şemaları için embedding kolonu.
-- V5'teki DO bloğu tek seferliktir; o yüzden ayrı bir migration ile koşullu olarak
-- tekrar denenir. Extension yoksa şema değişmez (fail-safe).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = 'rag_documents' AND column_name = 'embedding'
        ) THEN
            ALTER TABLE rag_documents ADD COLUMN embedding vector(1536);
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM pg_indexes
            WHERE schemaname = current_schema()
              AND tablename = 'rag_documents' AND indexname = 'idx_rag_documents_embedding'
        ) THEN
            BEGIN
                CREATE INDEX idx_rag_documents_embedding
                    ON rag_documents USING hnsw (embedding vector_cosine_ops);
            EXCEPTION WHEN OTHERS THEN
                -- HNSW oluşturulamazsa (ör. sürüm desteği yok) arama yine çalışır.
                RAISE NOTICE 'HNSW index oluşturulamadı: %', SQLERRM;
            END;
        END IF;
    END IF;
END $$;
