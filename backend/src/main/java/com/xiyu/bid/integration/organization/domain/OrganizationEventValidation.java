package com.xiyu.bid.integration.organization.domain;

public record OrganizationEventValidation(
        boolean valid,
        OrganizationEventType type,
        String message
) {
    public static OrganizationEventValidation ok(OrganizationEventType type) {
        return new OrganizationEventValidation(true, type, "success");
    }

    public static OrganizationEventValidation invalid(String message) {
        return new OrganizationEventValidation(false, null, message);
    }
}
