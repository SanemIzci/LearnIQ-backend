-- docker-entrypoint-initdb.d init script: pgvector uzantısı (Faz 4/5 rag_documents.embedding için).
-- Not: Bu script yalnızca YENİ volume'larda çalışır; mevcut postgres-data volume'u ile
-- CREATE EXTENSION vector; elle çalıştırılmalı ya da volume sıfırlanmalıdır
-- (docker compose down -v && docker compose up -d).
CREATE EXTENSION IF NOT EXISTS vector;
