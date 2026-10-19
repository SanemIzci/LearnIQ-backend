package com.learniq.exam.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("IllegalStateException should return HTTP 409 CONFLICT")
    void handleIllegalStateException_returns409() {
        IllegalStateException ex = new IllegalStateException("Sınav kapalı durumda, sonuç girilemez");

        ResponseEntity<ApiError> response = handler.handleIllegalStateException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().getStatus());
        assertEquals("State Conflict", response.getBody().getError());
        assertEquals("Sınav kapalı durumda, sonuç girilemez", response.getBody().getMessages().get(0));
    }

    @Test
    @DisplayName("IllegalArgumentException should return HTTP 400 BAD_REQUEST")
    void handleIllegalArgumentException_returns400() {
        IllegalArgumentException ex = new IllegalArgumentException("Toplam soru sayısı aşıldı");

        ResponseEntity<ApiError> response = handler.handleIllegalArgumentException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("Bad Request", response.getBody().getError());
        assertEquals("Toplam soru sayısı aşıldı", response.getBody().getMessages().get(0));
    }
}
