package com.xiyu.bid.businessqualification.domain.valueobject;

public enum QualificationStatus {
    IN_STOCK,
    EXPIRING,
    EXPIRED,
    RETIRED,
    @Deprecated VALID;

    public boolean isActive() {
        return this == IN_STOCK || this == EXPIRING || this == VALID;
    }

    public String toDisplayName() {
        if (this == IN_STOCK || this == VALID) return "在库";
        if (this == EXPIRING) return "即将到期";
        if (this == EXPIRED) return "已过期";
        if (this == RETIRED) return "已下架";
        return name();
    }
}
