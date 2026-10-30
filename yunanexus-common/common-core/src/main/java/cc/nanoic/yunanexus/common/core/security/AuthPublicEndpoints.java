package cc.nanoic.yunanexus.common.core.security;

import java.util.List;

/**
 * Auth's public HTTP contract, shared by the servlet service and reactive gateway.
 * Exact method/path pairs prevent an external, stale wildcard from exposing new auth endpoints.
 * CSRF and endpoint-specific credentials are still enforced by auth.
 */
public final class AuthPublicEndpoints {
    public record Endpoint(String method, String path) {}

    private static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint("GET", "/api/v1/auth/csrf"),
            new Endpoint("GET", "/api/v1/auth/captcha"),
            new Endpoint("POST", "/api/v1/auth/login"),
            new Endpoint("POST", "/api/v1/auth/register"),
            new Endpoint("POST", "/api/v1/auth/email-code"),
            new Endpoint("POST", "/api/v1/auth/refresh"),
            new Endpoint("POST", "/api/v1/auth/unlock-appeal"),
            new Endpoint("POST", "/api/v1/auth/password-reset/request"),
            new Endpoint("POST", "/api/v1/auth/password-reset/verify"),
            new Endpoint("POST", "/api/v1/auth/password-reset/complete"),
            new Endpoint("POST", "/api/v1/auth/sso/token"),
            new Endpoint("POST", "/api/v1/auth/sso/authorize"));

    private AuthPublicEndpoints() {}

    public static List<Endpoint> endpoints() {
        return ENDPOINTS;
    }

    public static boolean matches(String method, String path) {
        return ENDPOINTS.stream().anyMatch(e -> e.method().equals(method) && e.path().equals(path));
    }

    public static boolean isAuthPath(String path) {
        return "/api/v1/auth".equals(path) || path.startsWith("/api/v1/auth/");
    }
}
