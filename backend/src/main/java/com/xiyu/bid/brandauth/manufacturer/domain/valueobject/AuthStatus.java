package com.xiyu.bid.brandauth.manufacturer.domain.valueobject;

public enum AuthStatus {
    DRAFT("草稿"),
    ACTIVE("生效中"),
    EXPIRING_SOON("即将到期"),
    EXPIRED("已失效"),
    REVOKED("已作废");

    private final String displayName;

    AuthStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
