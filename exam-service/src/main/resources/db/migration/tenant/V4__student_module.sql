-- =======================================================
-- V4__student_module.sql
-- Öğrenci modülü geliştirmeleri (Approval, Ranking, AI Hash, Badges)
-- Location: classpath:db/migration/tenant
-- =======================================================

-- 1. Her öğrenci sadece bir sınıfa ait olabilir (N:N olan tablo 1:N gibi davranacak)
-- Eğer DB'de bir öğrencinin birden fazla sınıfı varsa bu migration hata verecektir (veri temizliği gerekir).
ALTER TABLE classroom_students ADD CONSTRAINT uk_classroom_students_student_id UNIQUE (student_id);

-- 2. Sınav Yaşam Döngüsü ve Sınıf Ataması
ALTER TABLE exams ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'OPEN'; -- OPEN | FINALIZED
ALTER TABLE exams ADD COLUMN created_by UUID;
ALTER TABLE exams ADD COLUMN finalized_at TIMESTAMP;

CREATE TABLE exam_classrooms (
    exam_id      UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    classroom_id UUID NOT NULL REFERENCES classrooms(id) ON DELETE CASCADE,
    PRIMARY KEY (exam_id, classroom_id)
);

-- 3. Net Hesaplama Kuralları (Sınav Türü + Ders bazlı)
CREATE TABLE exam_type_subject_rules (
    exam_type        VARCHAR(50) NOT NULL,
    subject_id       UUID NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    question_count   INT NOT NULL,
    penalty_divisor  DECIMAL(3,1) NOT NULL, -- TYT: 4.0, LGS: 3.0
    PRIMARY KEY (exam_type, subject_id)
);

-- 4. Öğrenci Sınav Sonuçları - Onay Akışı ve Sıralama
ALTER TABLE student_exam_results ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED'; -- SUBMITTED | APPROVED | REJECTED
ALTER TABLE student_exam_results ADD COLUMN entry_method VARCHAR(20) NOT NULL DEFAULT 'MANUAL'; -- MANUAL | OCR | TEACHER_BULK
ALTER TABLE student_exam_results ADD COLUMN reviewed_by UUID;
ALTER TABLE student_exam_results ADD COLUMN reviewed_at TIMESTAMP;
ALTER TABLE student_exam_results ADD COLUMN rejection_reason VARCHAR(500);
ALTER TABLE student_exam_results ADD COLUMN percentile DECIMAL(5,2);

-- 5. Kullanıcılar (Öğrenci) KVKK İzinleri
ALTER TABLE users ADD COLUMN leaderboard_consent BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN leaderboard_consent_at TIMESTAMP;

-- 6. AI Raporları (Tekilleştirme ve Metadata)
CREATE TABLE ai_reports (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id    UUID NOT NULL,
    data_hash     VARCHAR(64) NOT NULL, -- Onaylı sonuçların SHA-256'sı
    status        VARCHAR(20) NOT NULL, -- PENDING | READY | FAILED
    s3_key        VARCHAR(500),
    summary       TEXT,                 -- Bir önceki raporun özeti
    exam_count    INT NOT NULL,
    requested_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at  TIMESTAMP,
    UNIQUE (student_id, data_hash)
);

-- 7. Paylaşım Linkleri (Snapshot)
CREATE TABLE shared_links (
    token       VARCHAR(43) PRIMARY KEY, -- Base64Url (32 byte)
    student_id  UUID NOT NULL,
    snapshot    JSONB NOT NULL,          -- Paylaşım anındaki verinin dondurulmuş hali
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at  TIMESTAMP NOT NULL,
    revoked_at  TIMESTAMP
);

-- 8. Rozetler (Badges)
CREATE TABLE badges (
    code        VARCHAR(50) PRIMARY KEY,
    title       VARCHAR(100) NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE student_badges (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id  UUID NOT NULL,
    badge_code  VARCHAR(50) NOT NULL REFERENCES badges(code) ON DELETE CASCADE,
    exam_id     UUID REFERENCES exams(id) ON DELETE CASCADE,
    awarded_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (student_id, badge_code, exam_id)
);
