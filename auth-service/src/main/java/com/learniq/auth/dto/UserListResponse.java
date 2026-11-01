package com.learniq.auth.dto;

import java.util.List;

public record UserListResponse(
        long totalElements,
        int page,
        int size,
        List<AdminUserSummary> content) {
}
