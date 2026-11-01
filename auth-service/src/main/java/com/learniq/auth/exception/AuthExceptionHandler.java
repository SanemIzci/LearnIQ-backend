package com.learniq.auth.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fail-closed sözleşme: eksik header → 400, yanlış rol → 403, eşsizlik ihlali → 409.
 */
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        return build(ex, ex.getStatusCode().value());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingHeader(MissingRequestHeaderException ex) {
        return build(ex, HttpStatus.BAD_REQUEST.value());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        return build(ex, HttpStatus.CONFLICT.value());
    }

    private ResponseEntity<Map<String, Object>> build(Exception ex, int statusCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", statusCode);
        body.put("timestamp", Instant.now().toString());
        body.put("message", ex.getMessage() != null ? ex.getMessage() : "");
        return ResponseEntity.status(statusCode).body(body);
    }
}
