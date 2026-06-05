package com.xiyu.bid.admin.settings.core;

import java.util.Set;

public final class OrganizationValidationPolicy {
    private OrganizationValidationPolicy() {
    }

    public static OrganizationValidationResult validateUserOrganization(
            boolean enabled,
            String departmentCode,
            Long roleId,
            Set<String> existingDepartmentCodes,
            Set<Long> enabledRoleIds
    ) {
        if (!enabled) {
            return OrganizationValidationResult.ok();
        }
        boolean missingDepartment = departmentCode == null
                || !existingDepartmentCodes.contains(
                        DepartmentGraphPolicy.normalizeCode(departmentCode));
        if (missingDepartment) {
            return OrganizationValidationResult.invalid("启用用户必须绑定有效部门");
        }
        if (roleId == null || !enabledRoleIds.contains(roleId)) {
            return OrganizationValidationResult.invalid("启用用户必须绑定启用角色");
        }
        return OrganizationValidationResult.ok();
    }

    public static OrganizationValidationResult validateRoleDeactivation(
            boolean nextEnabled,
            int userCount
    ) {
        if (!nextEnabled && userCount > 0) {
            return OrganizationValidationResult.invalid("角色已分配给用户，不能直接停用");
        }
        return OrganizationValidationResult.ok();
    }
}
