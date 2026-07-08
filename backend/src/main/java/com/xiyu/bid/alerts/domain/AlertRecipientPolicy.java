package com.xiyu.bid.alerts.domain;

import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.entity.RoleProfileCatalog;

import java.util.List;

/**
 * 告警接收人角色解析纯核心。
 *
 * <p>负责根据 {@link AlertRule.AlertType} 解析应当接收通知的角色码列表。
 * 角色码取自 {@link RoleProfileCatalog} 标准角色定义，确保单一真相来源。</p>
 *
 * <p>FP-Java Profile 合规：</p>
 * <ul>
 *   <li>纯静态方法，无 Spring 注解、无 Repository/Service 依赖、无 IO</li>
 *   <li>不抛异常：null 入参返回空列表（安全降级）</li>
 *   <li>返回值不可变（{@link List#copyOf(Object)} 保证 unmodifiable）</li>
 * </ul>
 */
public final class AlertRecipientPolicy {

    private AlertRecipientPolicy() {
        // 纯核心工具类，禁止实例化
    }

    /**
     * 根据告警类型解析接收人角色码列表。
     *
     * <p>返回值始终非 null 且不可变。null 入参返回空列表，调用方安全降级。</p>
     *
     * <p><b>设计决策</b>：大部分告警类型采用角色码广播策略。
     * DEADLINE 类型<b>不广播</b>，仅通过
     * {@link #requiresProjectSpecificRecipients} 走项目精准通知，
     * 避免投标专员收到所有标讯的截止提醒（通知轰炸）。
     * 其他类型仍走角色广播。</p>
     *
     * @param type 告警类型，null 时返回空列表
     * @return 不可变角色码列表，非 null
     */
    public static List<String> resolveRoleCodes(AlertRule.AlertType type) {
        if (type == null) {
            return List.of();
        }
        List<String> raw = switch (type) {
            // P1-4: DEADLINE 不再广播，仅通过 requiresProjectSpecificRecipients 走项目精准通知
            case DEADLINE -> List.of();
            case RISK -> List.of(RoleProfileCatalog.BID_ADMIN_CODE, RoleProfileCatalog.BID_LEAD_CODE);
            case DOCUMENT -> List.of(RoleProfileCatalog.SALES_CODE, RoleProfileCatalog.BID_LEAD_CODE);
            case BUDGET -> List.of(RoleProfileCatalog.BID_ADMIN_CODE, RoleProfileCatalog.BID_LEAD_CODE);
            case DEPOSIT_RETURN -> List.of(RoleProfileCatalog.BID_ADMIN_CODE, RoleProfileCatalog.ADMIN_STAFF_CODE);
            case PERFORMANCE_EXPIRY -> List.of(RoleProfileCatalog.BID_ADMIN_CODE, RoleProfileCatalog.BID_LEAD_CODE);
            case CA_EXPIRY -> List.of(RoleProfileCatalog.BID_ADMIN_CODE, RoleProfileCatalog.BID_LEAD_CODE);
            case CA_BORROW_OVERDUE -> List.of(RoleProfileCatalog.BID_ADMIN_CODE, RoleProfileCatalog.BID_LEAD_CODE);
            case QUALIFICATION_EXPIRY -> List.of(RoleProfileCatalog.ADMIN_STAFF_CODE, RoleProfileCatalog.BID_ADMIN_CODE, RoleProfileCatalog.BID_LEAD_CODE);
        };
        return List.copyOf(raw);
    }

    /**
     * 判断该告警类型是否需要按项目相关人精准通知（而非仅角色广播）。
     *
     * <p>DEADLINE 告警应通知标讯关联项目的负责人，而非所有项目Leader，
     * 避免通知轰炸和信息泄露。编排层据此调用
     * {@code NotificationRecipientResolver.getProjectMemberUserIds(projectId)} 补充接收人。</p>
     *
     * @param type 告警类型
     * @return true 表示需要按项目解析接收人
     */
    public static boolean requiresProjectSpecificRecipients(AlertRule.AlertType type) {
        return type == AlertRule.AlertType.DEADLINE;
    }
}
