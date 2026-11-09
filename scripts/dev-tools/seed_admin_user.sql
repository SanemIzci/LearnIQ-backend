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
    '$2a$10$NWuYjalQXSyXaIl3TIJKI.GT9VAYypgzUZuQcSgSF1MZasdik01RW',
    'admin@akademi1.com',
    'Akademi 1 Admin',
    'ADMIN'
) ON CONFLICT (username) DO NOTHING;
