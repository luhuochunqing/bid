package com.xiyu.bid.integration.organization.domain;

public record OrganizationUserSnapshot(
        String externalUserId,
        String username,
        String fullName,
        String email,
        String phone,
        String departmentCode,
        String departmentName,
        String jobId,
        String externalRoleCode,
        boolean enabled
) {
}
