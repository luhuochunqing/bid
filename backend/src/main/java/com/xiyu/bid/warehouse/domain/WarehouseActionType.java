package com.xiyu.bid.warehouse.domain;

public enum WarehouseActionType {
    CREATE("创建"),
    EDIT("编辑"),
    CLOSE("关仓"),
    RESTORE("恢复"),
    ATTACH_UPLOAD("上传附件"),
    ATTACH_DELETE("删除附件");

    private final String displayName;
    WarehouseActionType(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
