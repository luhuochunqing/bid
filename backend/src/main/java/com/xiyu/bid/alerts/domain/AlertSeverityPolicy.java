package com.xiyu.bid.alerts.domain;

import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;

/**
 * 告警严重级别决策纯核心。
 *
 * <p>负责根据 {@link AlertRule.AlertType}（及 DEADLINE 的阈值天数）决定
 * {@link AlertHistory.AlertLevel}，供告警创建时统一调用。</p>
 *
 * <p>FP-Java Profile 合规：</p>
 * <ul>
 *   <li>纯静态方法，无 Spring 注解、无 Repository/Service 依赖、无 IO</li>
 *   <li>不抛异常：null 入参返回 {@link AlertHistory.AlertLevel#MEDIUM}（安全降级）</li>
 *   <li>无 setter、无状态、无副作用</li>
 * </ul>
 */
public final class AlertSeverityPolicy {

    /** DEADLINE 阈值 ≤1 天为紧急。 */
    private static final int DEADLINE_CRITICAL_THRESHOLD_DAYS = 1;
    /** DEADLINE 阈值 ≤3 天为高。 */
    private static final int DEADLINE_HIGH_THRESHOLD_DAYS = 3;

    private AlertSeverityPolicy() {
        // 纯核心工具类，禁止实例化
    }

    /**
     * 根据告警规则类型解析严重级别。
     *
     * <p>DEADLINE 类型额外参考阈值天数：≤1 天为 CRITICAL，≤3 天为 HIGH，否则 MEDIUM。
     * 其他类型按固定映射。</p>
     *
     * @param rule 告警规则，null 时返回 {@link AlertHistory.AlertLevel#MEDIUM}
     * @return 严重级别，非 null
     */
    public static AlertHistory.AlertLevel resolveSeverity(AlertRule rule) {
        if (rule == null || rule.getType() == null) {
            return AlertHistory.AlertLevel.MEDIUM;
        }
        return switch (rule.getType()) {
            case BUDGET -> AlertHistory.AlertLevel.HIGH;
            case DEADLINE -> resolveDeadlineSeverity(rule);
            case RISK, DEPOSIT_RETURN -> AlertHistory.AlertLevel.MEDIUM;
            case DOCUMENT -> AlertHistory.AlertLevel.LOW;
            case QUALIFICATION_EXPIRY, PERFORMANCE_EXPIRY, CA_EXPIRY, CA_BORROW_OVERDUE -> AlertHistory.AlertLevel.HIGH;
        };
    }

    private static AlertHistory.AlertLevel resolveDeadlineSeverity(AlertRule rule) {
        int days = rule.getThreshold() == null ? Integer.MAX_VALUE : rule.getThreshold().intValue();
        if (days <= DEADLINE_CRITICAL_THRESHOLD_DAYS) {
            return AlertHistory.AlertLevel.CRITICAL;
        }
        if (days <= DEADLINE_HIGH_THRESHOLD_DAYS) {
            return AlertHistory.AlertLevel.HIGH;
        }
        return AlertHistory.AlertLevel.MEDIUM;
    }
}
