-- ===================================================================
-- V3: Classroom Teachers (N:N)
-- Bir sınıfın birden fazla öğretmeni olabilir
-- (ör: 11-A → Matematik, Fen, Türkçe, Sosyal, İngilizce hocaları)
-- ===================================================================

-- 1. Yeni junction tablosu: classroom_teachers
CREATE TABLE classroom_teachers (
    classroom_id UUID NOT NULL REFERENCES classrooms(id) ON DELETE CASCADE,
    teacher_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    assigned_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (classroom_id, teacher_id)
);

-- 2. Mevcut classrooms.teacher_id verisini junction tabloya taşı
INSERT INTO classroom_teachers (classroom_id, teacher_id)
SELECT id, teacher_id
FROM classrooms
WHERE teacher_id IS NOT NULL;

-- 3. Eski teacher_id kolonunu classrooms'tan kaldır
ALTER TABLE classrooms DROP COLUMN teacher_id;

-- 4. Index
CREATE INDEX idx_classroom_teachers_teacher ON classroom_teachers(teacher_id);
CREATE INDEX idx_classroom_teachers_classroom ON classroom_teachers(classroom_id);
