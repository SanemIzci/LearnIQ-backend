-- =======================================================
-- LEARNIQ TEST SEED DATA
-- Run this AFTER Flyway has initialized the tenant schema.
-- Target: tenant_akademi_1
-- =======================================================

SET search_path TO tenant_akademi_1;

-- 1. Insert Mock Exam
INSERT INTO exams (id, title, exam_date, exam_type)
VALUES (
    'aaaaaaaa-0000-0000-0000-000000000001',
    'Haziran TYT Denemesi',
    '2025-06-15',
    'TYT'
) ON CONFLICT (id) DO NOTHING;

-- 2. Insert Mock Subjects
INSERT INTO subjects (id, name)
VALUES
    ('bbbbbbbb-0000-0000-0000-000000000001', 'Mathematics'),
    ('bbbbbbbb-0000-0000-0000-000000000002', 'Turkish')
ON CONFLICT (id) DO NOTHING;

-- 3. Map Subjects to the Exam (bridge table)
INSERT INTO exam_subjects (exam_id, subject_id)
VALUES
    ('aaaaaaaa-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000001'),
    ('aaaaaaaa-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000002')
ON CONFLICT DO NOTHING;

-- =======================================================
-- Also seed the public schema with the tenant entry
-- so Flyway and Hibernate can resolve the tenant.
-- =======================================================

SET search_path TO public;

INSERT INTO tenants (id, name, subscription_status)
VALUES (
    'akademi_1',
    'Akademi Bir Test Academy',
    'ACTIVE'
) ON CONFLICT (id) DO NOTHING;
