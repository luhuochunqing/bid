package com.xiyu.bid.alerts.domain;

/**
 * 告警历史去重策略（纯核心枚举，无框架依赖）。
 *
 * <p>不同告警类型对"何时新建告警"有不同需求：
 * <ul>
 *   <li>{@link #REUSE_UNTIL_RESOLVED}：未处理告警一直复用，直到被人工 resolve。
 *       适用于 DEADLINE/预算/保证金等"一次性告警 + 人工处理"场景。</li>
 *   <li>{@link #DAILY_DEDUP}：未处理告警当日复用，次日新建以触发每日通知。
 *       适用于 CA 到期等"持续预警 + 每日提醒"场景（CO-546）。</li>
 * </ul>
 *
 * <p>默认策略为 {@link #REUSE_UNTIL_RESOLVED}，保持历史行为不变。</p>
 */
public enum DedupPolicy {
    /** 未处理告警一直复用，直到被人工 resolve（原行为，默认） */
    REUSE_UNTIL_RESOLVED,
    /** 未处理告警当日复用，次日新建以触发每日通知（CO-546 CA 到期预警） */
    DAILY_DEDUP
}
