package com.learniq.exam.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DataFingerprintServiceTest {

    private final DataFingerprintService service = new DataFingerprintService();

    @Test
    void generateHash_samePayload_returnsSameHash() {
        String payload1 = "Total:90.50|Percentile:12.50|Matematik:C35W5N33.75";
        String payload2 = "Total:90.50|Percentile:12.50|Matematik:C35W5N33.75";

        String hash1 = service.generateHash(payload1);
        String hash2 = service.generateHash(payload2);

        assertNotNull(hash1);
        assertEquals(64, hash1.length()); // SHA-256 is 64 hex characters
        assertEquals(hash1, hash2);
    }

    @Test
    void generateHash_differentPayload_returnsDifferentHash() {
        String payload1 = "Total:90.50";
        String payload2 = "Total:90.51";

        String hash1 = service.generateHash(payload1);
        String hash2 = service.generateHash(payload2);

        assertNotEquals(hash1, hash2);
    }
}
