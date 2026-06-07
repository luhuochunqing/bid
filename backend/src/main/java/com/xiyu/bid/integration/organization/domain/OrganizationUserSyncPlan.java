package com.xiyu.bid.integration.organization.domain;

public record OrganizationUserSyncPlan(
        String username,
        String fullName,
        String email,
        String phone,
        String departmentCode,
        String departmentName,
        String roleCode,
        boolean enabled,
        boolean deleteUser
) {
    public OrganizationUserSyncPlan(
            String username,
            String fullName,
            String email,
            String phone,
            String departmentCode,
            String departmentName,
            String roleCode,
            boolean enabled
    ) {
        this(username, fullName, email, phone, departmentCode, departmentName, roleCode, enabled, false);
    }
}
