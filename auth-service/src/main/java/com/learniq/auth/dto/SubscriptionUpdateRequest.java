package com.learniq.auth.dto;

/**
 * ACTIVE | SUSPENDED | EXPIRED — whitelist dışı değerler 400.
 */
public record SubscriptionUpdateRequest(String status) {
}
