-- ===================================================================
-- Seed: SUPER_ADMIN user for public.global_users
-- Password: Admin@1234  (Verified BCrypt hash)
-- Target: public schema
-- ===================================================================

SET search_path TO public;

INSERT INTO global_users (id, username, password_hash, email, role)
VALUES (
    'ffffffff-0000-0000-0000-000000000001',
    'superadmin',
    '$2a$10$NWuYjalQXSyXaIl3TIJKI.GT9VAYypgzUZuQcSgSF1MZasdik01RW',
    'superadmin@learniq.local',
    'SUPER_ADMIN'
)
ON CONFLICT (username) 
DO UPDATE SET password_hash = '$2a$10$NWuYjalQXSyXaIl3TIJKI.GT9VAYypgzUZuQcSgSF1MZasdik01RW';

SELECT id, username, email, role FROM public.global_users WHERE username = 'superadmin';
