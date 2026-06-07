package com.xiyu.bid.admin.settings.core;

import java.util.List;

public record RoleAccessRule(String dataScope, List<Long> allowedProjectIds, List<String> allowedDeptCodes) {
    public RoleAccessRule {
        allowedProjectIds = allowedProjectIds == null ? List.of() : List.copyOf(allowedProjectIds);
        allowedDeptCodes = allowedDeptCodes == null ? List.of() : List.copyOf(allowedDeptCodes);
    }
}
