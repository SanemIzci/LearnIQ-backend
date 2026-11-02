package com.learniq.auth.dto;

import java.time.LocalDateTime;

public record TenantSummary(
        String id,
        String name,
        String domain,
        String subscriptionStatus,
        LocalDateTime createdAt) {
}
