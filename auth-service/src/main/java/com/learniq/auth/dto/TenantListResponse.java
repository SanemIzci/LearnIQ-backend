package com.learniq.auth.dto;

import java.util.List;

public record TenantListResponse(
        long totalElements,
        int page,
        int size,
        List<TenantSummary> content) {
}
