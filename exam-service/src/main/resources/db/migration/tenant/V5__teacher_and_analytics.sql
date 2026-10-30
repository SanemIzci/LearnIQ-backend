-- =======================================================
-- V5__teacher_and_analytics.sql
-- Öğretmen Modülü, Sınıf Trend Fact'leri ve RAG Temelleri
-- Location: classpath:db/migration/tenant
-- =======================================================

-- 1. Konu / Kazanım Havuzu (Subject Topics)
CREATE TABLE subject_topics (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id  UUID NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    name        VARCHAR(150) NOT NULL,
    code        VARCHAR(50),
    order_index INT DEFAULT 0,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (subject_id, name)
);

CREATE INDEX idx_subject_topics_subject ON subject_topics(subject_id);

-- 2. Öğretmen Gözlem Notları (Semantik RAG Besini)
CREATE TABLE teacher_observations (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    classroom_id UUID NOT NULL REFERENCES classrooms(id) ON DELETE CASCADE,
    subject_id   UUID REFERENCES subjects(id) ON DELETE SET NULL,
    exam_id      UUID REFERENCES exams(id) ON DELETE SET NULL,
    content      TEXT NOT NULL,
    tags         JSONB DEFAULT '[]',
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_teacher_obs_classroom ON teacher_observations(classroom_id);
CREATE INDEX idx_teacher_obs_teacher ON teacher_observations(teacher_id);
CREATE INDEX idx_teacher_obs_exam ON teacher_observations(exam_id);

-- 3. Önceden Hesaplanmış Sınıf-Ders Metrikleri (Sayısal RAG Fact Tablosu)
CREATE TABLE class_subject_trends (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    classroom_id           UUID NOT NULL REFERENCES classrooms(id) ON DELETE CASCADE,
    subject_id             UUID NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    exam_id                UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    avg_net                DECIMAL(5,2) NOT NULL,
    avg_score              DECIMAL(6,2),
    student_count          INT NOT NULL,
    expected_student_count INT,
    participation_rate     DECIMAL(5,2),
    delta_from_previous    DECIMAL(5,2),
    trend_direction        VARCHAR(10) NOT NULL CHECK (trend_direction IN ('UP', 'DOWN', 'STABLE')),
    calculated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (classroom_id, subject_id, exam_id)
);

CREATE INDEX idx_class_subject_trends_classroom ON class_subject_trends(classroom_id);
CREATE INDEX idx_class_subject_trends_exam ON class_subject_trends(exam_id);

-- 4. RAG Dokümanları (PostgreSQL Semantik Depo)
CREATE TABLE rag_documents (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_type  VARCHAR(50) NOT NULL,
    source_id    UUID NOT NULL,
    classroom_id UUID,
    subject_id   UUID,
    content      TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    metadata     JSONB DEFAULT '{}',
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (source_type, source_id)
);

CREATE INDEX idx_rag_documents_lookup ON rag_documents(source_type, classroom_id, subject_id);

-- PostgreSQL pgvector eklentisi DB seviyesinde aktif ise embedding sütunu ve HNSW index'i eklenir.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_extension WHERE extname = 'vector'
    ) THEN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'rag_documents' AND column_name = 'embedding'
        ) THEN
            ALTER TABLE rag_documents ADD COLUMN embedding vector(1536);
            CREATE INDEX IF NOT EXISTS idx_rag_documents_embedding ON rag_documents USING hnsw (embedding vector_cosine_ops);
        END IF;
    END IF;
END $$;
