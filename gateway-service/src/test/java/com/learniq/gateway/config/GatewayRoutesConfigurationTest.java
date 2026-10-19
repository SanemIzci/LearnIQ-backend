package com.learniq.gateway.config;

import com.learniq.gateway.filter.JwtAuthenticationFilter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Gateway route tanımları (application.yml)")
class GatewayRoutesConfigurationTest {

    private static final String AUTH_SERVICE_URI = "http://localhost:8081";
    private static final String EXAM_SERVICE_URI = "http://localhost:8082";

    private static List<Map<String, Object>> routes;
    private static Map<String, Map<String, Object>> routesById;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void loadRoutes() {
        try (InputStream in = GatewayRoutesConfigurationTest.class.getResourceAsStream("/application.yml")) {
            assertNotNull(in, "application.yml bulunamadı");
            Map<String, Object> root = (Map<String, Object>) new Yaml().load(in);
            Map<String, Object> spring = (Map<String, Object>) root.get("spring");
            Map<String, Object> cloud = (Map<String, Object>) spring.get("cloud");
            Map<String, Object> gateway = (Map<String, Object>) cloud.get("gateway");
            routes = (List<Map<String, Object>>) gateway.get("routes");
        } catch (Exception e) {
            throw new IllegalStateException("Gateway rotaları okunamadı", e);
        }
        assertNotNull(routes, "spring.cloud.gateway.routes tanımlı değil");
        routesById = routes.stream().collect(Collectors.toMap(r -> (String) r.get("id"), Function.identity()));
    }

    @SuppressWarnings("unchecked")
    private static List<String> filtersOf(String id) {
        Object filters = routesById.get(id).get("filters");
        if (filters == null) {
            return List.of();
        }
        return ((List<Object>) filters).stream().map(String::valueOf).toList();
    }

    @SuppressWarnings("unchecked")
    private static String pathExpressionOf(String id) {
        List<Object> predicates = (List<Object>) routesById.get(id).get("predicates");
        return predicates.stream().map(String::valueOf)
                .filter(p -> p.startsWith("Path="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Route '" + id + "' için Path predicate tanımlı değil"));
    }

    private static boolean matchesAnyPath(String requestPath) {
        return routes.stream().map(r -> (String) r.get("id"))
                .map(GatewayRoutesConfigurationTest::pathExpressionOf)
                .map(expr -> expr.substring("Path=".length()))
                .flatMap(patterns -> java.util.Arrays.stream(patterns.split(",")))
                .map(String::trim)
                .anyMatch(pattern -> {
                    if (pattern.endsWith("/**")) {
                        return requestPath.startsWith(pattern.substring(0, pattern.length() - 3))
                                || requestPath.equals(pattern.substring(0, pattern.length() - 3));
                    }
                    return requestPath.equals(pattern);
                });
    }

    @Test
    @DisplayName("Beklenen tüm route id'leri tanımlı")
    void allExpectedRoutesAreDeclared() {
        assertTrue(routesById.keySet().containsAll(List.of(
                        "auth-public", "auth-protected", "tenant-service",
                        "platform-auth", "exam-public", "exam-service")),
                "Eksik route var. Mevcut: " + routesById.keySet());
    }

    @Test
    @DisplayName("auth-public ve auth-protected doğru path ve downstream uri ile tanımlı")
    void authRoutes() {
        assertEquals(AUTH_SERVICE_URI, routesById.get("auth-public").get("uri"));
        String publicPath = pathExpressionOf("auth-public");
        assertTrue(publicPath.contains("/api/v1/auth/login"), "login public olmalı");
        assertTrue(publicPath.contains("/api/v1/auth/refresh"), "refresh public olmalı");

        assertEquals(AUTH_SERVICE_URI, routesById.get("auth-protected").get("uri"));
        String protectedPath = pathExpressionOf("auth-protected");
        assertTrue(protectedPath.contains("/api/v1/auth/register"), "register korumalı olmalı");
        assertTrue(protectedPath.contains("/api/v1/auth/me"), "me korumalı olmalı");
        assertTrue(protectedPath.contains("/api/v1/auth/logout"), "logout korumalı olmalı");
    }

    @Test
    @DisplayName("exam-public /public, exam-service korumalı path'leri 8082'ye yönlendirir")
    void examRoutes() {
        assertEquals(EXAM_SERVICE_URI, routesById.get("exam-public").get("uri"));
        assertTrue(pathExpressionOf("exam-public").contains("/api/v1/public/"));

        assertEquals(EXAM_SERVICE_URI, routesById.get("exam-service").get("uri"));
        String protectedPath = pathExpressionOf("exam-service");
        assertTrue(protectedPath.contains("/api/v1/exams/**"), "exams korumalı olmalı");
        assertTrue(protectedPath.contains("/api/v1/analytics/**"), "analytics korumalı olmalı");
        assertTrue(protectedPath.contains("/api/v1/classrooms/**"), "classrooms korumalı olmalı");
        assertTrue(protectedPath.contains("/api/v1/reports/**"), "reports korumalı olmalı");
        assertTrue(protectedPath.contains("/api/v1/students/**"), "students korumalı olmalı");
    }

    @Test
    @DisplayName("tenant ve platform route'ları auth-service'e açık yönlenir")
    void tenantAndPlatformRoutes() {
        assertEquals(AUTH_SERVICE_URI, routesById.get("tenant-service").get("uri"));
        assertTrue(pathExpressionOf("tenant-service").contains("/api/v1/tenants/**"));

        assertEquals(AUTH_SERVICE_URI, routesById.get("platform-auth").get("uri"));
        assertTrue(pathExpressionOf("platform-auth").contains("/api/v1/platform/**"));
    }

    @Test
    @DisplayName("Public route'larda JWT filtresi yok, korumalı route'larda var")
    void authFilterAppliedOnlyToProtectedRoutes() {
        for (String openRoute : List.of("auth-public", "tenant-service", "platform-auth", "exam-public")) {
            assertTrue(filtersOf(openRoute).isEmpty(),
                    "Public route '" + openRoute + "' JWT filtresi içermemeli ama içeriyor: " + filtersOf(openRoute));
        }
        for (String protectedRoute : List.of("auth-protected", "exam-service")) {
            assertTrue(filtersOf(protectedRoute).contains("JwtAuthenticationFilter"),
                    "Korumalı route '" + protectedRoute + "' JwtAuthenticationFilter içermeli: " + filtersOf(protectedRoute));
        }
    }

    @Test
    @DisplayName("YAML'daki JwtAuthenticationFilter adı bean factory adıyla eşleşir")
    void jwtFilterNameResolvesToFactory() {
        JwtAuthenticationFilter factory = new JwtAuthenticationFilter();
        assertEquals("JwtAuthenticationFilter", factory.name(),
                "Filter factory adı YAML'daki referansla eşleşmezse gateway route'u başlatılamaz");
    }

    @Test
    @DisplayName("API yüzeyindeki her prefix en az bir route ile eşleşir (route kapsama)")
    void everyKnownApiPrefixIsRouted() {
        List<String> knownPaths = List.of(
                "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/register",
                "/api/v1/auth/me", "/api/v1/auth/logout",
                "/api/v1/tenants/acme", "/api/v1/platform/auth/login",
                "/api/v1/public/share/abc",
                "/api/v1/exams", "/api/v1/analytics/students/1/history",
                "/api/v1/classrooms", "/api/v1/reports/students/1",
                "/api/v1/students/me/home",
                "/api/v1/subjects");
        List<String> unrouted = knownPaths.stream()
                .filter(p -> !matchesAnyPath(p))
                .toList();
        assertTrue(unrouted.isEmpty(),
                "Bu endpoint'ler gateway'de hiçbir route ile eşleşmiyor (404 döner): " + unrouted);
    }
}
