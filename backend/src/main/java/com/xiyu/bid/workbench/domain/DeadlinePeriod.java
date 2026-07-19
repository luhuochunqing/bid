package com.xiyu.bid.workbench.domain;

/**
 * 工作台截止时间模块时间筛选周期（CO-593）。
 *
 * <p>对应前端 today / week / month 三个 Tab。</p>
 */
public enum DeadlinePeriod {
    TODAY,
    WEEK,
    MONTH;

    /**
     * 从字符串解析周期，不区分大小写。非法值回退到 {@link #WEEK}。
     */
    public static DeadlinePeriod fromStringOrDefault(String raw) {
        if (raw == null || raw.isBlank()) {
            return WEEK;
        }
        try {
            return DeadlinePeriod.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return WEEK;
        }
    }
}
