-- =======================================================
-- V8: Paylaşım linki token'ına tenant öneki eklendiği için
-- token kolonu genişletilir.
-- Format: <tenantSchema>.<43 karakter Base64Url>
-- (public endpoint'te X-Tenant-Id header'ı olmadığından,
-- satırın hangi kiracı şemasında aranacağı token üzerinden çözülür.)
-- =======================================================

ALTER TABLE shared_links ALTER COLUMN token TYPE VARCHAR(64);
