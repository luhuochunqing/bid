package com.xiyu.bid.performance.domain.model;


/**
 * 业绩合同到期提醒全局配置。
 *
 * 纯核心（不可变 record），蓝图表格 §4.5.1.9 定义两类差异化提醒窗口：
 * - 央企客户（CENTRAL_SOE）：提前 180 天
 * - 其他客户类型：提前 90 天
 *
 * 应用服务只做编排，数据访问委托给 {@code PerformanceAlertConfigRepository}。
 */
public record PerformanceAlertConfig(
        Long id,
        int alertDaysSoe,
        int alertDaysDefault,
        boolean enabled
) {

    public PerformanceAlertConfig {
        if (alertDaysSoe < 1 || alertDaysSoe > 365) {
            throw new IllegalArgumentException(
                    "alertDaysSoe must be between 1 and 365, got: " + alertDaysSoe);
        }
        if (alertDaysDefault < 1 || alertDaysDefault > 365) {
            throw new IllegalArgumentException(
                    "alertDaysDefault must be between 1 and 365, got: " + alertDaysDefault);
        }
    }

    /**
     * 根据客户类型返回对应的提醒天数。
     *
     * @param isSoe 是否为央企（CENTRAL_SOE）
     * @return 对应提醒窗口天数
     */
    public int alertDays(boolean isSoe) {
        return isSoe ? alertDaysSoe : alertDaysDefault;
    }

    /**
     * 判断给定剩余天数是否触发了到期提醒。
     *
     * @param isSoe          是否为央企
     * @param daysUntilExpiry 距到期的剩余天数（必须 > 0）
     * @return 启用提醒且剩余天数在窗口内则返回 true
     */
    public boolean isExpiring(boolean isSoe, long daysUntilExpiry) {
        return enabled && daysUntilExpiry > 0 && daysUntilExpiry <= alertDays(isSoe);
    }
}
