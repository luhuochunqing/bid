package com.xiyu.bid.enums;

public enum DataScopeType {
    ALL("all", "全部数据"),
    DEPT_AND_SUB("deptAndSub", "本部门及下级"),
    DEPT("dept", "本部门"),
    SELF("self", "仅本人"),
    CUSTOM("custom", "自定义项目组");

    private final String code;
    private final String description;

    DataScopeType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}