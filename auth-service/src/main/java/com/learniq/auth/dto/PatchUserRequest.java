package com.learniq.auth.dto;

/**
 * PATCH gövdesi: sadece verilen alanlar güncellenir (null = dokunma).
 */
public record PatchUserRequest(
        Boolean active,
        String fullName,
        String email) {
}
