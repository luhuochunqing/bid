package com.xiyu.bid.businessqualification.domain.model;

/**
 * 资质证书到期提醒全局告警规则配置。
 * <p>
 * 纯核心（不可变 record），负责规则计算。
 * 应用服务只做编排，不在此类中混合数据访问或 DTO 转换。
 */
public record AlertConfig(Long id, int alertDays, boolean enabled) {

    public AlertConfig {
        if (alertDays < 1 || alertDays > 365) {
            throw new IllegalArgumentException("alertDays must be between 1 and 365, got: " + alertDays);
        }
    }

    /**
     * 判断给定剩余天数是否触发了到期提醒。
     *
     * @param daysUntilExpiry 距离到期的剩余天数
     * @return 如果告警启用且剩余天数 <= 配置的提醒天数，返回 true
     */
    public boolean isExpiring(long daysUntilExpiry) {
        return enabled && daysUntilExpiry <= alertDays;
    }
}
