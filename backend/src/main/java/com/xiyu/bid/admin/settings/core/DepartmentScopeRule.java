package com.xiyu.bid.admin.settings.core;

import java.util.List;

public record DepartmentScopeRule(String departmentCode, String dataScope, List<String> allowedDeptCodes) {
    public DepartmentScopeRule {
        allowedDeptCodes = allowedDeptCodes == null ? List.of() : List.copyOf(allowedDeptCodes);
    }
}
