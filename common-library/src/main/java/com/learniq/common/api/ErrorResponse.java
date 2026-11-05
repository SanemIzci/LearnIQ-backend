package com.learniq.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Shared, standardized error response contract for all services.
 *
 * Shape:
 * {
 *   "timestamp": "2026-11-05T12:00:00Z",
 *   "status": 400,
 *   "error": "VALIDATION_FAILED",
 *   "message": "Request validation failed",
 *   "details": ["'title' must not be blank"],
 *   "path": "/api/v1/exams"
 * }
 *
 * `message` is a human-readable summary; `details` carries per-item
 * information (e.g. validation field errors) and is omitted when null.
 * Internal exception messages must never be exposed as-is.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        List<String> details,
        String path
) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, null, path);
    }

    public static ErrorResponse of(int status, String error, String message, List<String> details, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, details, path);
    }

    @com.fasterxml.jackson.annotation.JsonProperty("messages")
    public List<String> messages() {
        if (details != null && !details.isEmpty()) {
            return details;
        }
        return message != null ? List.of(message) : List.of();
    }
}
