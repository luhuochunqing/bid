package com.xiyu.bid.performance.domain.valueobject;

/** 客户级别枚举（蓝图 4.5） */
public enum CustomerLevel {
    GROUP("集团"),
    SUBSIDIARY("二级单位");

    private final String label;

    CustomerLevel(String label) {
        this.label = label;
    }

    public String displayName() {
        return label;
    }
}
