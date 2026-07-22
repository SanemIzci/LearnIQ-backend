-- ==========================================
-- V2__seed_test_tenant.sql
-- Description: Seed initial test tenant
-- Target: public schema
-- ==========================================

INSERT INTO tenants (id, name, subscription_status)
VALUES (
    'akademi_1',
    'Akademi Bir Test Academy',
    'ACTIVE'
) ON CONFLICT (id) DO NOTHING;
