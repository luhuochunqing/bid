package com.xiyu.bid.task.core;

import com.xiyu.bid.entity.RoleProfileCatalog;

/**
 * CO-361: 任务可见性策略 — 基于角色 + 项目身份判断任务可见范围。
 *
 * <p>纯核心：无状态、无依赖、无副作用。所有方法静态、参数显式传入。
 *
 * <p>权限矩阵（对齐 docs/permission-matrix/投标项目-权限矩阵.md §2.3.1）：
 * <ul>
 *   <li>投标管理角色（admin/ bidAdmin/ bid-TeamLeader）→ 看项目所有任务</li>
 *   <li>投标项目负责人（bid-projectLeader）且匹配 primaryLeadUserId → 看该项目所有任务</li>
 *   <li>投标专员（bid-Team）且是项目投标负责人/辅助 → 看该项目所有任务</li>
 *   <li>其他角色（跨部门 bid-otherDept、行政人员等）→ 只看 assignee=自己 的任务</li>
 * </ul>
 *
 * <p>使用 {@link RoleProfileCatalog} 常量判断角色，不硬编码 roleCode 字符串。
 */
public final class TaskVisibilityPolicy {

    private TaskVisibilityPolicy() {
    }

    /**
     * 判断用户是否可以查看指定项目的所有任务。
     *
     * <p>判定规则：
     * <ol>
     *   <li>角色属于 {@link RoleProfileCatalog#GLOBAL_ACCESS_ROLES}（admin/ bidAdmin/ bid-TeamLeader）→ true</li>
     *   <li>角色为 {@link RoleProfileCatalog#SALES_CODE}（投标项目负责人），
     *       且 userId 匹配该项目的 primaryLeadUserId → true</li>
     *   <li>角色为 {@link RoleProfileCatalog#BID_SPECIALIST_CODE}（投标专员），
     *       且 userId 匹配该项目的 primaryLeadUserId 或 secondaryLeadUserId → true</li>
     *   <li>其他情况 → false（只看 assignee=自己 的任务）</li>
     * </ol>
     *
     * @param roleCode           当前用户角色码
     * @param userId             当前用户 ID
     * @param primaryLeadUserId  项目主投标负责人 ID（可为 null）
     * @param secondaryLeadUserId 项目副投标负责人 ID（可为 null）
     * @return true 表示可查看项目所有任务；false 表示只看 assignee=自己 的任务
     */
    public static boolean canViewAllProjectTasks(
            final String roleCode,
            final Long userId,
            final Long primaryLeadUserId,
            final Long secondaryLeadUserId) {
        if (roleCode == null || roleCode.isBlank()) {
            return false;
        }
        // 投标管理角色（admin/ bidAdmin/ bid-TeamLeader）→ 看所有任务
        if (RoleProfileCatalog.GLOBAL_ACCESS_ROLES.contains(roleCode)) {
            return true;
        }
        // 投标项目负责人（bid-projectLeader）→ 需匹配主负责人
        if (RoleProfileCatalog.SALES_CODE.equalsIgnoreCase(roleCode)) {
            return userId != null && userId.equals(primaryLeadUserId);
        }
        // 投标专员（bid-Team）→ 需是项目投标负责人/辅助才看所有任务
        if (RoleProfileCatalog.BID_SPECIALIST_CODE.equalsIgnoreCase(roleCode)) {
            return matchesAnyLead(userId, primaryLeadUserId, secondaryLeadUserId);
        }
        // 其他角色（跨部门协同、行政人员等）→ 只看自己的任务
        return false;
    }

    /**
     * 判断用户在独立任务看板是否应按项目维度查询任务。
     *
     * <p>用于 {@code TaskBoardService.getBoardItems}：投标管理角色和投标专员
     * 都应按项目维度查询（而非仅查 assignee=自己）。
     *
     * <p>与 {@link #canViewAllProjectTasks} 的区别：
     * <ul>
     *   <li>本方法只判断"是否需要按项目查"（不关心是否是某项目的负责人）</li>
     *   <li>{@link #canViewAllProjectTasks} 判断"在特定项目内是否看所有任务"</li>
     * </ul>
     *
     * @param roleCode 当前用户角色码
     * @return true 表示应按项目维度查询；false 表示只查 assignee=自己
     */
    public static boolean shouldQueryByProjectScope(final String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return false;
        }
        // 投标管理角色 + 投标项目负责人 + 投标专员都按项目维度查
        return RoleProfileCatalog.GLOBAL_ACCESS_ROLES.contains(roleCode)
                || RoleProfileCatalog.SALES_CODE.equalsIgnoreCase(roleCode)
                || RoleProfileCatalog.BID_SPECIALIST_CODE.equalsIgnoreCase(roleCode);
    }

    private static boolean matchesAnyLead(Long userId, Long primaryLeadUserId, Long secondaryLeadUserId) {
        if (userId == null) {
            return false;
        }
        return userId.equals(primaryLeadUserId) || userId.equals(secondaryLeadUserId);
    }
}
