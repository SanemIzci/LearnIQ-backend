package com.learniq.auth.config;

import com.learniq.auth.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtAuthenticationFilter Unit Tests")
class JwtAuthenticationFilterTest {

    private static final String SECRET = "mustBeAtLeast32CharsLongForHS256Security!";
    private static final UUID USER_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiryMs", 3_600_000L);
        filter = new JwtAuthenticationFilter(jwtService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Geçerli JWT SecurityContext'e rol yazar ve sahte header'ları ezer")
    void validToken_setsAuthenticationAndOverridesHeaders() throws Exception {
        String token = jwtService.generateAccessToken(USER_ID, "ayse", "akademi_1", "TEACHER");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        request.addHeader("X-User-Role", "SUPER_ADMIN");
        request.addHeader("X-Tenant-Id", "baska_tenant");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicBoolean invoked = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> {
            invoked.set(true);
            assertThat(((HttpServletRequest) req).getHeader("X-User-Role")).isEqualTo("TEACHER");
            assertThat(((HttpServletRequest) req).getHeader("X-Tenant-Id")).isEqualTo("akademi_1");
            assertThat(((HttpServletRequest) req).getHeader("X-User-Id")).isEqualTo(USER_ID.toString());
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(invoked).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_TEACHER");
    }

    @Test
    @DisplayName("Header'sız istek zincirden geçer; Security karar verir")
    void noAuthorizationHeader_continuesWithoutAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> invoked.set(true);

        filter.doFilter(request, response, chain);

        assertThat(invoked).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Geçersiz JWT 401 döner ve zincir çağrılmaz")
    void invalidToken_returnsUnauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer gecersiz.token.degeri");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> invoked.set(true);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(invoked).isFalse();
    }

    @Test
    @DisplayName("Refresh token access token olarak kabul edilmez")
    void refreshToken_isRejected() throws Exception {
        String refreshToken = jwtService.generateRefreshToken(USER_ID, "akademi_1");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("SUPER_ADMIN token'ında X-Tenant-Id kaldırılır")
    void superAdmin_removesTenantHeader() throws Exception {
        String token = jwtService.generateSuperAdminToken(USER_ID, "platform_admin");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/platform/admin/tenants");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        request.addHeader("X-Tenant-Id", "akademi_1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicBoolean invoked = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> {
            invoked.set(true);
            assertThat(((HttpServletRequest) req).getHeader("X-User-Role")).isEqualTo("SUPER_ADMIN");
            assertThat(((HttpServletRequest) req).getHeader("X-Tenant-Id")).isNull();
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(invoked).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_SUPER_ADMIN");
    }
}
