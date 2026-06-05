package com.xiyu.bid.performance.domain.valueobject;

/**
 * 合同状态枚举（蓝图 4.5）
 * 状态由系统根据截止日期和客户类型自动计算，不由用户手动设置
 */
public enum ContractStatus {
    IN_PERFORMANCE,  // 履约中
    EXPIRING,        // 即将到期
    EXPIRED;         // 已到期

    public String displayName() {
        if (this == IN_PERFORMANCE) {
            return "履约中";
        }
        if (this == EXPIRING) {
            return "即将到期";
        }
        if (this == EXPIRED) {
            return "已到期";
        }
        return "";
    }
}
