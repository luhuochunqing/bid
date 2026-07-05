// Input: 角色码字符串
// Output: boolean — 是否为合法的项目转移新负责人角色
// Pos: project/core/ - 纯核心校验
// 维护声明: 纯函数，无副作用；角色集合来源于 RoleProfileCatalog.GLOBAL_ACCESS_ROLES + SALES_CODE。

package com.xiyu.bid.project.core;

import com.xiyu.bid.entity.RoleProfileCatalog;

import java.util.Set;

/**
 * 项目转移新负责人角色校验策略。
 * <p>
 * 合法角色 = {@link RoleProfileCatalog#GLOBAL_ACCESS_ROLES}（admin/bidAdmin/bidTeamLeader）
 * + {@link RoleProfileCatalog#SALES_CODE}（bid-projectLeader）。
 * </p>
 * <p>
 * 角色码来自被转移人 DB {@code role_profile} 快照（与 UserPicker 选人数据源一致），
 * 而非 OSS 登录缓存。校验使用大小写敏感精确匹配（Constitution / project_memory 硬约束：
 * 禁止在代码任何位置对 roleCode 做大小写归一化）。
 * </p>
 */
public final class ProjectTransferRolePolicy {

    private static final Set<String> VALID_NEW_OWNER_ROLES = Set.of(
            RoleProfileCatalog.ADMIN_CODE,
            RoleProfileCatalog.BID_ADMIN_CODE,
            RoleProfileCatalog.BID_LEAD_CODE,
            RoleProfileCatalog.SALES_CODE
    );

    private ProjectTransferRolePolicy() {
    }

    /**
     * 校验角色码是否为合法的项目转移新负责人角色。
     *
     * @param roleCode 角色码（来自被转移人 DB role_profile 快照）
     * @return true 如果角色为 admin//bidAdmin/bid-TeamLeader/bid-projectLeader 之一
     */
    public static boolean isValidNewOwnerRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return false;
        }
        return VALID_NEW_OWNER_ROLES.contains(roleCode);
    }
}
