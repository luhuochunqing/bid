// Input: endpoint metadata and Spring Security expression text
// Output: normalized role, access-level and risk classification
// Pos: admin permissions pure core
package com.xiyu.bid.admin.permissions.core;

import java.util.List;
import java.util.Locale;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

public final class EndpointPermissionPolicy {
    private static final List<String> AUTHENTICATED_ROLES = List.of("ADMIN", "MANAGER", "STAFF");
    private static final Pattern QUOTED_TOKEN = Pattern.compile("['\"]([A-Z_]+)['\"]");

    public EndpointPermissionDescriptor describe(
            String method,
            String path,
            String controller,
            String handler,
            String expression
    ) {
        String normalizedMethod = normalize(method, "ANY").toUpperCase(Locale.ROOT);
        String normalizedPath = normalize(path, "/");
        String normalizedExpression = normalizeExpression(expression, normalizedPath);
        List<String> roles = allowedRoles(normalizedExpression);

        return new EndpointPermissionDescriptor(
                normalizedMethod,
                normalizedPath,
                moduleName(normalizedPath),
                normalize(controller, ""),
                normalize(handler, ""),
                normalizedExpression,
                roles,
                accessLevel(normalizedExpression, roles),
                riskLevel(normalizedMethod, normalizedPath, roles),
                false,
                source(expression, normalizedPath),
                "入口层权限矩阵；不展开 Service 内部 hasAuthority 等二次授权"
        );
    }

    private String normalizeExpression(String expression, String path) {
        String trimmed = normalize(expression, "");
        if (!trimmed.isBlank()) return trimmed;
        if (isPublicApi(path)) return "permitAll()";
        if (path.startsWith("/api/admin/")) return "hasRole('ADMIN')";
        if (path.startsWith("/api/manager/")) return "hasAnyRole('ADMIN', 'MANAGER')";
        return "isAuthenticated()";
    }

    private List<String> allowedRoles(String expression) {
        if (expression.contains("permitAll")) return List.of();
        if (expression.contains("isAuthenticated")) return AUTHENTICATED_ROLES;
        if (expression.contains("hasRole") || expression.contains("hasAnyRole")) {
            return QUOTED_TOKEN.matcher(expression).results()
                    .map(MatchResult::group)
                    .map(token -> token.replace("'", "").replace("\"", ""))
                    .distinct()
                    .toList();
        }
        return List.of();
    }

    private String accessLevel(String expression, List<String> roles) {
        if (expression.contains("permitAll")) return "PUBLIC";
        if (expression.contains("hasAuthority")) return "AUTHORITY_BASED";
        if (roles.equals(List.of("ADMIN"))) return "ADMIN_ONLY";
        if (roles.equals(List.of("ADMIN", "MANAGER"))) return "ADMIN_MANAGER";
        if (roles.equals(AUTHENTICATED_ROLES)) return "AUTHENTICATED";
        return roles.isEmpty() ? "CUSTOM_EXPRESSION" : "ROLE_BASED";
    }

    private String riskLevel(String method, String path, List<String> roles) {
        if (path.startsWith("/api/admin/") || path.contains("password") || path.contains("secret")) {
            return "HIGH";
        }
        if (List.of("POST", "PUT", "PATCH", "DELETE").contains(method)
                || path.contains("export")
                || path.contains("download")
                || roles.equals(List.of("ADMIN", "MANAGER"))) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String source(String expression, String path) {
        if (!normalize(expression, "").isBlank()) return "METHOD_PRE_AUTHORIZE";
        if (isPublicApi(path)) return "SECURITY_WHITELIST_MIRROR";
        if (path.startsWith("/api/admin/") || path.startsWith("/api/manager/")) return "SECURITY_ROUTE_RULE_MIRROR";
        return "DEFAULT_AUTHENTICATED_MIRROR";
    }

    private boolean isPublicApi(String path) {
        return path.equals("/api/auth/login")
                || path.equals("/api/auth/register")
                || path.equals("/api/auth/logout")
                || path.equals("/api/auth/refresh")
                || path.equals("/api/auth/forgot-password")
                || path.equals("/api/auth/reset-password")
                || path.equals("/api/auth/sessions")
                || path.startsWith("/api/auth/verify-email")
                || path.startsWith("/api/public/")
                || path.equals("/api/system/runtime-mode");
    }

    private String moduleName(String path) {
        String[] parts = path.split("/");
        if (parts.length > 2 && "api".equals(parts[1])) {
            return parts[2].replaceAll("\\{.*}", "dynamic");
        }
        return "system";
    }

    private String normalize(String value, String fallback) {
        return value == null ? fallback : value.trim();
    }
}
