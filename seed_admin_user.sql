-- ===================================================================
-- Seed: Initial ADMIN user for akademi_1 tenant
-- Password: Admin@1234  (BCrypt hash)
-- Run after Flyway migrations have created the users table.
-- ===================================================================

SET search_path TO akademi_1;

INSERT INTO users (id, username, password_hash, email, full_name, role)
VALUES (
    'eeeeeeee-0000-0000-0000-000000000001',
    'akademi_admin',
    -- BCrypt hash of 'Admin@1234'
    '$2a$12$7v9SYvnWi9grBMJQy6Laze8T3zULQ7bMNgGS8aJsC.Fz1eXovBkS6',
    'admin@akademi1.com',
    'Akademi 1 Admin',
    'ADMIN'
) ON CONFLICT (username) DO NOTHING;
