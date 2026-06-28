package com.xiyu.bid.security;

import java.util.Optional;

/**
 * 角色码缓存读取端口（依赖倒置，打破 security ↔ crm 循环依赖）。
 *
 * <p>位于 security 包的 {@link EffectiveRoleResolver} 需要读取 OSS 权限缓存的角色码，
 * 但缓存实现 {@code com.xiyu.bid.crm.application.OssPermissionCache} 位于 crm 包。
 * 直接依赖会形成 crm → security（登录白名单）与 security → crm（缓存读取）的循环依赖。
 *
 * <p>本端口反转依赖方向：security 仅依赖此接口，crm 包的 OssPermissionCache 实现它，
 * Spring 按类型注入。这样 security 不再单向依赖 crm，循环被打破。</p>
 *
 * <p>仅暴露读取角色码能力，避免泄漏缓存内部结构（菜单权限等由 crm 自行消费）。</p>
 */
public interface RoleCodeCachePort {

    /**
     * 读取缓存中用户的角色码。
     *
     * @param username 用户名
     * @return 命中时返回角色码；未命中返回 {@link Optional#empty()}
     */
    Optional<String> getRoleCode(String username);
}
