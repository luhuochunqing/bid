package com.xiyu.bid.admin.settings.core;

import java.util.List;

public record UserScopeRule(Long userId, String dataScope, List<Long> allowedProjectIds, List<String> allowedDeptCodes) {
    public UserScopeRule {
        allowedProjectIds = allowedProjectIds == null ? List.of() : List.copyOf(allowedProjectIds);
        allowedDeptCodes = allowedDeptCodes == null ? List.of() : List.copyOf(allowedDeptCodes);
    }
}
