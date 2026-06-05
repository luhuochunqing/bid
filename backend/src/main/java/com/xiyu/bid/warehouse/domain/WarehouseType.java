package com.xiyu.bid.warehouse.domain;

public enum WarehouseType {
    SELF_OPERATED("自营"),
    CLOUD("云仓");

    private final String displayName;
    WarehouseType(String d) { this.displayName = d; }
    public String getDisplayName() { return displayName; }
}
