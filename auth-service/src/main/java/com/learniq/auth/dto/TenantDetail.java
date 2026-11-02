package com.learniq.auth.dto;

import java.time.LocalDateTime;

public record TenantDetail(
        String id,
        String name,
        String domain,
        String subscriptionStatus,
        LocalDateTime createdAt,
        long userCount) {
}
