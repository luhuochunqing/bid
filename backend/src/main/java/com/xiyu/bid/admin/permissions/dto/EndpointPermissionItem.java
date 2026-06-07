// Input: endpoint permission descriptor fields
// Output: API response item for endpoint permission matrix
// Pos: admin permissions DTO
package com.xiyu.bid.admin.permissions.dto;

import java.util.List;

public record EndpointPermissionItem(
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
