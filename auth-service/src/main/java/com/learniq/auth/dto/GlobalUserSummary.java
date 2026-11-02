package com.learniq.auth.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record GlobalUserSummary(UUID id, String username, String email, LocalDateTime createdAt) {
}
