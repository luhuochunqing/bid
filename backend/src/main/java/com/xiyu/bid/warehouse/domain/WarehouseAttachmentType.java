package com.xiyu.bid.warehouse.domain;

public enum WarehouseAttachmentType {
    PROPERTY_CERTIFICATE("产权证"),
    INVOICE("发票"),
    PHOTOS("内外照片");

    private final String displayName;
    WarehouseAttachmentType(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
