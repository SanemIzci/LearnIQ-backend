package com.learniq.auth.dto;

/**
 * Tenant provisioning isteği.
 * id: kiracı şema adı (TenantSchemaValidator regex'ine uymalı: ^[a-z][a-z0-9_]{0,62}$)
 * adminPassword verilmezse platform rastgele üretir (stonu tek seferlik yanıtta döner).
 */
public record CreateTenantRequest(
        String id,
        String name,
        String domain,
        String adminUsername,
        String adminEmail,
        String adminFullName,
        String adminPassword) {
}
