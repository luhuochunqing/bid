package com.xiyu.bid.alerts.domain;

import com.xiyu.bid.alerts.entity.AlertRule;

import java.util.List;

/**
 * 告警接收人角色解析纯核心。
 *
 * <p>负责根据 {@link AlertRule.AlertType} 解析应当接收通知的角色码列表。
 * 角色码取自 {@code RoleProfileCatalog} 标准角色定义（admin/bid-projectLeader/
 * bid-TeamLeader//bidAdmin/bid-administration 等）。</p>
 *
 * <p>FP-Java Profile 合规：</p>
 * <ul>
 *   <li>纯静态方法，无 Spring 注解、无 Repository/Service 依赖、无 IO</li>
 *   <li>不抛异常：null 入参返回空列表（安全降级）</li>
 *   <li>返回值不可变（{@link List#copyOf(Object)} 保证 unmodifiable）</li>
 * </ul>
 */
public final class AlertRecipientPolicy {

    /** 角色码常量，与 {@code RoleProfileCatalog} 保持一致。 */
    private static final String ROLE_PROJECT_LEADER = "bid-projectLeader";
    private static final String ROLE_TEAM_LEADER = "bid-TeamLeader";
    private static final String ROLE_BID_ADMIN = "/bidAdmin";
    private static final String ROLE_ADMINISTRATION = "bid-administration";

    private AlertRecipientPolicy() {
        // 纯核心工具类，禁止实例化
    }

    /**
     * 根据告警类型解析接收人角色码列表。
     *
     * <p>返回值始终非 null 且不可变（{@link UnsupportedOperationException} on mutate）。
     * null 入参返回空列表，调用方安全降级。</p>
     *
     * @param type 告警类型，null 时返回空列表
     * @return 不可变角色码列表，非 null
     */
    public static List<String> resolveRoleCodes(AlertRule.AlertType type) {
        if (type == null) {
            return List.of();
        }
        List<String> raw = switch (type) {
            case DEADLINE -> List.of(ROLE_PROJECT_LEADER, ROLE_TEAM_LEADER);
            case RISK -> List.of(ROLE_BID_ADMIN, ROLE_TEAM_LEADER);
            case DOCUMENT -> List.of(ROLE_PROJECT_LEADER, ROLE_TEAM_LEADER);
            case BUDGET -> List.of(ROLE_BID_ADMIN, ROLE_TEAM_LEADER);
            case DEPOSIT_RETURN -> List.of(ROLE_BID_ADMIN, ROLE_ADMINISTRATION);
            case PERFORMANCE_EXPIRY -> List.of(ROLE_BID_ADMIN, ROLE_TEAM_LEADER);
            case CA_EXPIRY -> List.of(ROLE_BID_ADMIN, ROLE_TEAM_LEADER);
            case CA_BORROW_OVERDUE -> List.of(ROLE_BID_ADMIN, ROLE_TEAM_LEADER);
            case QUALIFICATION_EXPIRY -> List.of(ROLE_ADMINISTRATION, ROLE_BID_ADMIN, ROLE_TEAM_LEADER);
        };
        return List.copyOf(raw);
    }
}
