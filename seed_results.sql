-- =======================================================
-- SEED STUDENT RESULTS DATA
-- Target: akademi_1
-- =======================================================

SET search_path TO akademi_1;

-- 1. Insert Subjects
INSERT INTO subjects (id, name) VALUES 
('bbbbbbbb-0000-0000-0000-000000000001', 'Mathematics'),
('bbbbbbbb-0000-0000-0000-000000000002', 'Turkish'),
('bbbbbbbb-0000-0000-0000-000000000003', 'Social Sciences'),
('bbbbbbbb-0000-0000-0000-000000000004', 'Science')
ON CONFLICT (name) DO NOTHING;

-- 2. Insert New Exam
INSERT INTO exams (id, title, exam_date, exam_type) VALUES (
    'aaaaaaaa-0000-0000-0000-000000000002',
    'TYT Genel Deneme 1',
    '2026-07-21',
    'TYT'
) ON CONFLICT (id) DO NOTHING;

-- 3. Map Subjects to the Exam
INSERT INTO exam_subjects (exam_id, subject_id) VALUES
('aaaaaaaa-0000-0000-0000-000000000002', 'bbbbbbbb-0000-0000-0000-000000000001'), -- Mathematics
('aaaaaaaa-0000-0000-0000-000000000002', 'bbbbbbbb-0000-0000-0000-000000000002'), -- Turkish
('aaaaaaaa-0000-0000-0000-000000000002', 'bbbbbbbb-0000-0000-0000-000000000003'), -- Social Sciences
('aaaaaaaa-0000-0000-0000-000000000002', 'bbbbbbbb-0000-0000-0000-000000000004')  -- Science
ON CONFLICT DO NOTHING;

-- 4. Insert Student Exam Result
INSERT INTO student_exam_results (id, student_id, exam_id, total_score, class_rank, school_rank)
VALUES (
    'cccccccc-0000-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    'aaaaaaaa-0000-0000-0000-000000000002',
    80.00,
    1,
    5
) ON CONFLICT (student_id, exam_id) DO UPDATE SET total_score = 80.00;

-- 5. Insert Student Subject Results
INSERT INTO student_subject_results (id, student_id, exam_id, subject_id, correct_count, wrong_count, net_score)
VALUES 
-- Mathematics: 25 Correct, 4 Incorrect (24.00 Net)
('dddddddd-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-0000-0000-0000-000000000002', 'bbbbbbbb-0000-0000-0000-000000000001', 25, 4, 24.00),
-- Turkish: 30 Correct, 5 Incorrect (28.75 Net)
('dddddddd-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-0000-0000-0000-000000000002', 'bbbbbbbb-0000-0000-0000-000000000002', 30, 5, 28.75),
-- Social Sciences: 15 Correct, 4 Incorrect (14.00 Net)
('dddddddd-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-0000-0000-0000-000000000002', 'bbbbbbbb-0000-0000-0000-000000000003', 15, 4, 14.00),
-- Science: 14 Correct, 3 Incorrect (13.25 Net)
('dddddddd-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'aaaaaaaa-0000-0000-0000-000000000002', 'bbbbbbbb-0000-0000-0000-000000000004', 14, 3, 13.25)
ON CONFLICT (student_id, exam_id, subject_id) DO UPDATE SET net_score = EXCLUDED.net_score;
