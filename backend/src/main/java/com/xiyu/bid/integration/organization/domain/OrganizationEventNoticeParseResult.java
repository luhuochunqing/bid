package com.xiyu.bid.integration.organization.domain;

public record OrganizationEventNoticeParseResult(
        boolean valid,
        OrganizationEventNotice notice,
        String message
) {
    public static OrganizationEventNoticeParseResult ok(OrganizationEventNotice notice) {
        return new OrganizationEventNoticeParseResult(true, notice, "success");
    }

    public static OrganizationEventNoticeParseResult invalid(String message) {
        return new OrganizationEventNoticeParseResult(false, null, message);
    }
}
