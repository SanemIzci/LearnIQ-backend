package com.learniq.exam.exception;

import com.learniq.common.api.ErrorResponse;
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

        ResponseEntity<ErrorResponse> response = handler.handleIllegalStateException(ex, null);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().status());
        assertEquals("State Conflict", response.getBody().error());
        assertEquals("Sınav kapalı durumda, sonuç girilemez", response.getBody().message());
    }

    @Test
    @DisplayName("IllegalArgumentException should return HTTP 400 BAD_REQUEST")
    void handleIllegalArgumentException_returns400() {
        IllegalArgumentException ex = new IllegalArgumentException("Toplam soru sayısı aşıldı");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgumentException(ex, null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertEquals("Bad Request", response.getBody().error());
        assertEquals("Toplam soru sayısı aşıldı", response.getBody().message());
    }
}
