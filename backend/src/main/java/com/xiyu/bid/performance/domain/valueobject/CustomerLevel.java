package com.xiyu.bid.performance.domain.valueobject;

/** 客户级别枚举（蓝图 4.5） */
public enum CustomerLevel {
    GROUP,       // 集团
    SUBSIDIARY;  // 二级单位

    public String displayName() {
        if (this == GROUP) {
            return "集团";
        }
        if (this == SUBSIDIARY) {
            return "二级单位";
        }
        return "";
    }
}
