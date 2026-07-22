-- =======================================================
-- EXAM ENGINE - MACRO-LEVEL MVP SCHEMA (PostgreSQL)
-- Location: classpath:db/migration/tenant
-- =======================================================

-- 1. Exams
-- Core exam metadata
CREATE TABLE exams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    exam_date DATE NOT NULL,
    exam_type VARCHAR(50) NOT NULL, -- e.g., 'TYT', 'AYT', 'LGS'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Subjects
-- Macro-level subjects (e.g., Mathematics, Turkish)
CREATE TABLE subjects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Exam Subjects Mapping
-- Defines which subjects were tested in a specific exam
CREATE TABLE exam_subjects (
    exam_id UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    PRIMARY KEY (exam_id, subject_id)
);

-- 4. Student Exam Results (Macro-Level)
-- Tracks the overall performance and ranking of a student in a specific exam
CREATE TABLE student_exam_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL, -- References a user ID
    exam_id UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    total_score DECIMAL(7,2) NOT NULL,
    class_rank INT,
    school_rank INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (student_id, exam_id)
);

-- 5. Student Subject Results (Micro-Level Net Scores)
-- Tracks the net scores per subject, feeding the AI for trend analysis
CREATE TABLE student_subject_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL,
    exam_id UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    correct_count INT NOT NULL DEFAULT 0,
    wrong_count INT NOT NULL DEFAULT 0,
    net_score DECIMAL(5,2) NOT NULL DEFAULT 0.0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (student_id, exam_id, subject_id)
);

-- Indexes for fast analytics and AI reporting queries
CREATE INDEX idx_student_exam_results_student ON student_exam_results(student_id);
CREATE INDEX idx_student_subject_results_student ON student_subject_results(student_id);
CREATE INDEX idx_student_subject_results_exam ON student_subject_results(exam_id);
