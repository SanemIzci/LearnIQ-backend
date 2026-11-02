package com.learniq.auth.dto;

/**
 * Provisioning yanıtı. temporaryPassword YALNIZCA bu yanıtta gösterilir —
 * tekrar okunamaz; adminPassword verilirse null döner.
 */
public record CreateTenantResponse(
        TenantSummary tenant,
        String adminUsername,
        String temporaryPassword) {
}
