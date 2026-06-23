package com.xiyu.bid.security.domain;

import com.xiyu.bid.entity.RoleProfileCatalog;

/**
 * 登录角色白名单。
 *
 * <p>系统仅允许 {@link RoleProfileCatalog} 中已注册的标准角色对应的用户登录；
 * 未注册的角色（包括历史 staff）视为无系统访问权限。
 *
 * <p>白名单由 {@link RoleProfileCatalog} 的 DEFINITIONS map 单一真相源驱动，
 * 新增角色只需在 RoleProfileCatalog 中定义，无需同步修改本类。
 *
 * <p>本类为纯核心（Pure Core）：不可变、无框架依赖、可独立单元测试。
 * 放置于 {@code security.domain} 以避免 auth/crm 等业务模块间循环依赖。
 */
public final class LoginRoleWhitelist {

    private LoginRoleWhitelist() {
    }

    /**
     * 判断给定角色码是否允许登录。
     *
     * <p>委托给 {@link RoleProfileCatalog#isRegisteredCode(String)}，以 DEFINITIONS 为单一真相源。
     *
     * @param roleCode 内部角色码，允许 null/blank
     * @return true 当且仅当 roleCode 在 {@link RoleProfileCatalog} 中已注册
     */
    public static boolean isAllowed(String roleCode) {
        return RoleProfileCatalog.isRegisteredCode(roleCode);
    }

    /**
     * 校验给定角色码，失败时抛出 {@link IllegalArgumentException}。
     */
    public static void requireAllowed(String roleCode) {
        if (!isAllowed(roleCode)) {
            throw new IllegalArgumentException("角色 " + roleCode + " 不在登录白名单中");
        }
    }
}
