package com.learniq.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;
    private final String secret = "mustBeAtLeast32CharsLongForHS256Security!";
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "secret", secret);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    private String generateToken(String role, String userId, String tenantId) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        var builder = Jwts.builder()
                .claim("role", role)
                .claim("userId", userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key);

        if (tenantId != null) {
            builder.claim("tenantId", tenantId);
        }

        return builder.compact();
    }

    @Test
    @DisplayName("No Authorization header -> 401 Unauthorized")
    void apply_noAuthHeader_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/students/me/home").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.apply(new JwtAuthenticationFilter.Config()).filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Valid Student JWT -> forwards identity headers")
    void apply_validStudentToken_forwardsHeaders() {
        String studentId = UUID.randomUUID().toString();
        String tenantId = "tenant_alpha";
        String token = generateToken("STUDENT", studentId, tenantId);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/students/me/home")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.apply(new JwtAuthenticationFilter.Config()).filter(exchange, chain).block();

        verify(chain, times(1)).filter(argThat(ex -> {
            HttpHeaders headers = ex.getRequest().getHeaders();
            assertEquals("STUDENT", headers.getFirst("X-User-Role"));
            assertEquals(studentId, headers.getFirst("X-User-Id"));
            assertEquals(tenantId, headers.getFirst("X-Tenant-Id"));
            return true;
        }));
    }

    @Test
    @DisplayName("Invalid JWT -> 401 Unauthorized")
    void apply_invalidToken_returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/students/me/home")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.apply(new JwtAuthenticationFilter.Config()).filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }
}
