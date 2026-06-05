// Input: endpoint mapping metadata and authorization expression
// Output: immutable endpoint permission decision for API catalog display
// Pos: admin permissions pure core
package com.xiyu.bid.admin.permissions.core;

import java.util.List;

public record EndpointPermissionDescriptor(
        String method,
        String path,
        String module,
        String controller,
        String handler,
        String expression,
        List<String> allowedRoles,
        String accessLevel,
        String riskLevel,
        boolean configurable,
        String source,
        String scopeNote
) {
}
