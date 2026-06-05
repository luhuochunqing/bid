package com.xiyu.bid.admin.settings.core;

public record OrganizationValidationResult(boolean valid, String message) {
    public static OrganizationValidationResult ok() {
        return new OrganizationValidationResult(true, "");
    }

    public static OrganizationValidationResult invalid(String message) {
        return new OrganizationValidationResult(false, message);
    }
}
