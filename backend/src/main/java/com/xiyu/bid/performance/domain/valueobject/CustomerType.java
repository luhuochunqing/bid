package com.xiyu.bid.performance.domain.valueobject;

/**
 * 客户类型枚举（蓝图 4.5）
 * 影响到期提醒规则：央企 180 天 / 其他 90 天
 */
public enum CustomerType {
    GOVERNMENT_INSTITUTION("政府机关/事业单位/高校"),
    CENTRAL_SOE("央企"),
    LOCAL_SOE("地方国企"),
    PRIVATE_ENTERPRISE("民企"),
    FOREIGN_HK_MACAO_TW("港澳台及外企");

    private final String label;

    CustomerType(String label) {
        this.label = label;
    }

    public String displayName() {
        return label;
    }
}
