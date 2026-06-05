package com.xiyu.bid.warehouse.domain;

public enum WarehouseStatus {
    IN_USE("使用中"), EXPIRING("即将到期"), EXPIRED("已到期"), CLOSED("已关仓");

    private final String displayName;
    WarehouseStatus(String d) { this.displayName = d; }
    public String getDisplayName() { return displayName; }
}
