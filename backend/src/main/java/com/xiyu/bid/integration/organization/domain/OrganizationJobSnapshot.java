package com.xiyu.bid.integration.organization.domain;

public record OrganizationJobSnapshot(
        String externalJobId,
        String jobCode,
        String jobName,
        boolean enabled
) {
}
