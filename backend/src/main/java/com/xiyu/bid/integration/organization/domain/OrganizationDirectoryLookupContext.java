package com.xiyu.bid.integration.organization.domain;

public record OrganizationDirectoryLookupContext(
        String traceId,
        String sourceApp,
        String ossToken
) {
    public static OrganizationDirectoryLookupContext empty() {
        return new OrganizationDirectoryLookupContext("", "", "");
    }

    public static OrganizationDirectoryLookupContext withOssToken(String ossToken) {
        return new OrganizationDirectoryLookupContext("", "", ossToken);
    }
}
