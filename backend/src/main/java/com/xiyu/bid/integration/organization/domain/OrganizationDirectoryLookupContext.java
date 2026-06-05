package com.xiyu.bid.integration.organization.domain;

public record OrganizationDirectoryLookupContext(
        String traceId,
        String sourceApp
) {
    public static OrganizationDirectoryLookupContext empty() {
        return new OrganizationDirectoryLookupContext("", "");
    }
}
