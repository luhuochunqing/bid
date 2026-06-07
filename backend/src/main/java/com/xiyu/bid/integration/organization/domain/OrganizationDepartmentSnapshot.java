package com.xiyu.bid.integration.organization.domain;

public record OrganizationDepartmentSnapshot(
        String externalDeptId,
        String departmentCode,
        String departmentName,
        String parentExternalDeptId,
        String parentDepartmentCode,
        boolean enabled
) {
}
