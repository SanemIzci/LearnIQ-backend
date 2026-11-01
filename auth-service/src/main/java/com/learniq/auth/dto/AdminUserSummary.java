package com.learniq.auth.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminUserSummary(
        UUID id,
        String username,
        String email,
        String fullName,
        String role,
        boolean active,
        LocalDateTime createdAt) {
}
