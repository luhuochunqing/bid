package com.xiyu.bid.performance.domain.valueobject;

/** 对接方式枚举（蓝图 4.5） */
public enum DockingMethod {
    EMALL,       // Emall
    PUNCH_OUT,   // Punch-out
    API;         // API

    public String displayName() {
        if (this == EMALL) {
            return "Emall";
        }
        if (this == PUNCH_OUT) {
            return "Punch-out";
        }
        if (this == API) {
            return "API";
        }
        return "";
    }
}
