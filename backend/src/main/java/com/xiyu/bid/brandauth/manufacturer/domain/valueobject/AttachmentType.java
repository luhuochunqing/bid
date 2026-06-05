package com.xiyu.bid.brandauth.manufacturer.domain.valueobject;

public enum AttachmentType {
    AUTH_DOC("原厂授权附件"),
    SUPPLEMENTARY("补充材料附件");

    private final String displayName;

    AttachmentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
