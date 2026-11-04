package com.learniq.auth.config;

import com.learniq.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * auth-service'in kendi güvenlik zinciri için JWT doğrulaması.
 *
 * Gateway kimlik header'larını zaten eziyor; ancak servis doğrudan erişilebilir
 * olduğu için yalnızca X-User-* header'larına güvenilemez. Bu filtre:
 * 1. Authorization: Bearer JWT'yi imza + expiry açısından doğrular,
 * 2. SecurityContext'e rol yetkisiyle Authentication yazar,
 * 3. X-User-Id / X-User-Role / X-Tenant-Id header'larını claim'lerden yeniden yazar
 *    (istemcinin gönderdiği sahte değerler controller'a ulaşamaz).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        Claims claims;
        try {
            claims = jwtService.parseToken(authHeader.substring(BEARER_PREFIX.length()));
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
            return;
        }

        String role = claims.get("role", String.class);
        String userId = claims.get("userId", String.class);
        String tenantId = claims.get("tenantId", String.class);
        String tokenType = claims.get("type", String.class);

        // Refresh token'lar access token olarak kullanılamaz; rolü olmayan token reddedilir.
        if (tokenType != null || role == null || role.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token type");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userId != null ? userId : claims.getSubject(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(
                new IdentityHeaderRequest(request, role, userId, tenantId), response);
    }

    /** Kimlik header'larını JWT claim'lerinden yazar; sahte istemci değerlerini ezer. */
    static final class IdentityHeaderRequest extends HttpServletRequestWrapper {

        private final Map<String, String> overridden;

        IdentityHeaderRequest(HttpServletRequest request, String role, String userId, String tenantId) {
            super(request);
            Map<String, String> values = new LinkedHashMap<>();
            values.put("x-user-role", role);
            if (userId != null) {
                values.put("x-user-id", userId);
            }
            // SUPER_ADMIN tenant-agnostiktir: tenant header'ı kaldırılır.
            if (tenantId != null && !"SUPER_ADMIN".equals(role)) {
                values.put("x-tenant-id", tenantId);
            }
            this.overridden = values;
        }

        @Override
        public String getHeader(String name) {
            String key = name == null ? null : name.toLowerCase(Locale.ROOT);
            if (key != null && overridden.containsKey(key)) {
                return overridden.get(key);
            }
            if (key != null && "x-tenant-id".equals(key)) {
                return null;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String key = name == null ? null : name.toLowerCase(Locale.ROOT);
            if (key != null && overridden.containsKey(key)) {
                return Collections.enumeration(List.of(overridden.get(key)));
            }
            if (key != null && "x-tenant-id".equals(key)) {
                return Collections.emptyEnumeration();
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>();
            Enumeration<String> original = super.getHeaderNames();
            if (original != null) {
                while (original.hasMoreElements()) {
                    String next = original.nextElement();
                    String key = next.toLowerCase(Locale.ROOT);
                    if (!overridden.containsKey(key) && !"x-tenant-id".equals(key)) {
                        names.add(next);
                    }
                }
            }
            overridden.forEach((key, value) -> names.add(key));
            return Collections.enumeration(names);
        }
    }
}
