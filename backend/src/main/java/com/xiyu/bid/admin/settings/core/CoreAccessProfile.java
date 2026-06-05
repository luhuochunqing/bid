package com.xiyu.bid.admin.settings.core;

import java.util.List;

public record CoreAccessProfile(String dataScope, List<Long> explicitProjectIds, List<String> allowedDepartmentCodes) {
    public CoreAccessProfile {
        explicitProjectIds = explicitProjectIds == null ? List.of() : List.copyOf(explicitProjectIds);
        allowedDepartmentCodes = allowedDepartmentCodes == null ? List.of() : List.copyOf(allowedDepartmentCodes);
    }

    public static CoreAccessProfile empty() {
        return new CoreAccessProfile("self", List.of(), List.of());
    }
}
