package com.xiyu.bid.admin.permissions.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointPermissionPolicyTest {

    private final EndpointPermissionPolicy policy = new EndpointPermissionPolicy();

    @Test
    void classifyAdminManagerExpression() {
        EndpointPermissionDescriptor descriptor = policy.describe(
                "GET",
                "/api/alerts/history/unresolved",
                "AlertHistoryController",
                "getUnresolvedAlertHistories",
                "hasAnyRole('ADMIN', 'MANAGER')"
        );

        assertThat(descriptor.allowedRoles()).containsExactly("ADMIN", "MANAGER");
        assertThat(descriptor.accessLevel()).isEqualTo("ADMIN_MANAGER");
        assertThat(descriptor.source()).isEqualTo("METHOD_PRE_AUTHORIZE");
        assertThat(descriptor.riskLevel()).isEqualTo("MEDIUM");
        assertThat(descriptor.configurable()).isFalse();
    }

    @Test
    void classifyImplicitAdminRouteWhenMethodExpressionMissing() {
        EndpointPermissionDescriptor descriptor = policy.describe(
                "GET",
                "/api/admin/permissions/endpoints",
                "AdminEndpointPermissionController",
                "list",
                null
        );

        assertThat(descriptor.allowedRoles()).containsExactly("ADMIN");
        assertThat(descriptor.accessLevel()).isEqualTo("ADMIN_ONLY");
        assertThat(descriptor.source()).isEqualTo("SECURITY_ROUTE_RULE_MIRROR");
        assertThat(descriptor.scopeNote()).contains("入口层权限");
    }

    @Test
    void classifySecurityWhitelistMirrorForPublicAuthApi() {
        EndpointPermissionDescriptor descriptor = policy.describe(
                "POST",
                "/api/auth/login",
                "AuthController",
                "login",
                null
        );

        assertThat(descriptor.allowedRoles()).isEmpty();
        assertThat(descriptor.accessLevel()).isEqualTo("PUBLIC");
        assertThat(descriptor.expression()).isEqualTo("permitAll()");
        assertThat(descriptor.source()).isEqualTo("SECURITY_WHITELIST_MIRROR");
    }

    @Test
    void classifyAuthenticatedFallbackWithoutPretendingItIsConfigurable() {
        EndpointPermissionDescriptor descriptor = policy.describe(
                "GET",
                "/api/projects",
                "ProjectController",
                "list",
                "isAuthenticated()"
        );

        assertThat(descriptor.allowedRoles()).isEqualTo(List.of("ADMIN", "MANAGER", "STAFF"));
        assertThat(descriptor.accessLevel()).isEqualTo("AUTHENTICATED");
        assertThat(descriptor.configurable()).isFalse();
    }
}
