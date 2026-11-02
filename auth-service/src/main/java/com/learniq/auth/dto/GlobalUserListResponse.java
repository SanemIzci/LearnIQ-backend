package com.learniq.auth.dto;

import java.util.List;

public record GlobalUserListResponse(
        long totalElements,
        int page,
        int size,
        List<GlobalUserSummary> content) {
}
