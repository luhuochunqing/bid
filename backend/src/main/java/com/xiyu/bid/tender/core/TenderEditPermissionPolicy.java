package com.xiyu.bid.tender.core;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 标讯基本信息编辑/删除权限的纯核心策略。
 *
 * <p>仅做值到值的规则计算：给定用户角色、标讯所有者与状态，返回是否可编辑、是否可删除。
 * 无 IO、无 Spring 注入、不写状态 — 真单元策略。
 *
 * <p>权限矩阵来源：飞书标讯权限矩阵（2026-06-17）。
 * <ul>
 *   <li>可编辑：admin / bid_admin / bid_lead / bid_senior 在未立项状态可编辑；
 *       sales 在自己是 creatorId 或 projectManagerId 且状态符合要求时可编辑。</li>
 *   <li>可删除：admin / bid_admin / bid_lead / bid_senior 在待分配/跟踪中可删除；
 *       sales 在自己创建且未评估前可删除。</li>
 * </ul>
 *
 * <p>{@code legacyRole} 保留在签名中以兼容现有调用上下文，当前矩阵以 {@code roleCode} 为准；
 * 未匹配到已定义规则的角色一律返回 {@code false}。
 */
public final class TenderEditPermissionPolicy {

    private TenderEditPermissionPolicy() {
        // 工具类不可实例化
    }

    private static final Set<Tender.Status> EDITABLE_STATUSES_FOR_GLOBAL_ROLES = Set.of(
            Tender.Status.PENDING_ASSIGNMENT,
            Tender.Status.TRACKING,
            Tender.Status.EVALUATED
    );

    private static final Set<Tender.Status> DELETABLE_STATUSES_FOR_GLOBAL_ROLES = Set.of(
            Tender.Status.PENDING_ASSIGNMENT,
            Tender.Status.TRACKING
    );

    private static final Set<Tender.Status> SALES_EDITABLE_STATUSES = Set.of(
            Tender.Status.TRACKING,
            Tender.Status.EVALUATED
    );

    /**
     * 判定当前用户是否允许编辑标讯基本信息。
     *
     * @param roleCode         用户角色码；null/空白/未注册角色均按无权限处理
     * @param legacyRole       保留参数，当前策略以 roleCode 为准
     * @param userId           当前用户 ID；null 时返回 false
     * @param creatorId        标讯创建人 ID
     * @param projectManagerId 标讯项目负责人 ID
     * @param status           标讯状态；null 时返回 false
     * @return true 表示可编辑
     */
    public static boolean canEdit(
            String roleCode,
            User.Role legacyRole,
            Long userId,
            Long creatorId,
            Long projectManagerId,
            Tender.Status status) {
        if (userId == null || status == null) {
            return false;
        }

        String normalizedRole = normalizeRole(roleCode);
        return switch (normalizedRole) {
            case "admin", "bid_admin", "bid_lead", "bid_senior" ->
                    EDITABLE_STATUSES_FOR_GLOBAL_ROLES.contains(status);
            case "sales" -> canSalesEdit(userId, creatorId, projectManagerId, status);
            default -> false;
        };
    }

    /**
     * 判定当前用户是否允许删除标讯。
     *
     * @param roleCode         用户角色码；null/空白/未注册角色均按无权限处理
     * @param legacyRole       保留参数，当前策略以 roleCode 为准
     * @param userId           当前用户 ID；null 时返回 false
     * @param creatorId        标讯创建人 ID
     * @param projectManagerId 标讯项目负责人 ID（删除规则不依赖此字段，保留签名一致性）
     * @param status           标讯状态；null 时返回 false
     * @return true 表示可删除
     */
    public static boolean canDelete(
            String roleCode,
            User.Role legacyRole,
            Long userId,
            Long creatorId,
            Long projectManagerId,
            Tender.Status status) {
        if (userId == null || status == null) {
            return false;
        }

        String normalizedRole = normalizeRole(roleCode);
        return switch (normalizedRole) {
            case "admin", "bid_admin", "bid_lead", "bid_senior" ->
                    DELETABLE_STATUSES_FOR_GLOBAL_ROLES.contains(status);
            case "sales" ->
                    DELETABLE_STATUSES_FOR_GLOBAL_ROLES.contains(status) && Objects.equals(creatorId, userId);
            default -> false;
        };
    }

    private static boolean canSalesEdit(Long userId, Long creatorId, Long projectManagerId, Tender.Status status) {
        if (status == Tender.Status.PENDING_ASSIGNMENT) {
            return Objects.equals(creatorId, userId);
        }
        if (SALES_EDITABLE_STATUSES.contains(status)) {
            return Objects.equals(creatorId, userId) || Objects.equals(projectManagerId, userId);
        }
        return false;
    }

    private static String normalizeRole(String roleCode) {
        return roleCode == null ? "" : roleCode.trim().toLowerCase(Locale.ROOT);
    }
}
