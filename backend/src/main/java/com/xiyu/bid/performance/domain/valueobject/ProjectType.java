package com.xiyu.bid.performance.domain.valueobject;

/** 项目类型枚举（蓝图 4.5） */
public enum ProjectType {
    OFFICE,          // 办公
    COMPREHENSIVE,   // 综合
    CENTRALIZED,     // 集采
    INDUSTRIAL,      // 工业品
    OTHER;           // 其他

    public String displayName() {
        if (this == OFFICE) {
            return "办公";
        }
        if (this == COMPREHENSIVE) {
            return "综合";
        }
        if (this == CENTRALIZED) {
            return "集采";
        }
        if (this == INDUSTRIAL) {
            return "工业品";
        }
        if (this == OTHER) {
            return "其他";
        }
        return "";
    }
}
