package com.xiyu.bid.performance.domain.valueobject;

/** 对接方式枚举（蓝图 4.5） */
public enum DockingMethod {
    EMALL("Emall"),
    PUNCH_OUT("Punch-out"),
    API("API");

    private final String label;

    DockingMethod(String label) {
        this.label = label;
    }

    public String displayName() {
        return label;
    }
}
