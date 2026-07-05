package com.xiyu.bid.platform.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.util.PlatformAccountContactMatcher;
import java.util.List;
import java.util.Locale;

/**
 * 账户列表查看权限策略（CO-388）。
 *
 * <p>纯静态策略，不依赖 Spring 上下文，便于单测。</p>
 */
public final class PlatformAccountViewerPolicy {

    private PlatformAccountViewerPolicy() {
    }

    /** 管理员 / 投标管理员 / 投标组长可查看完整记录。 */
    public static boolean isPrivilegedRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return false;
        }
        return switch (roleCode.toLowerCase(Locale.ROOT)) {
            case "admin", "bid-teamleader", "/bidadmin" -> true;
            default -> false;
        };
    }

    /** 投标专员需要按绑定联系人做逐行授权。 */
    public static boolean isBidTeamRole(String roleCode) {
        return roleCode != null && "bid-team".equalsIgnoreCase(roleCode.trim());
    }

    /**
     * CO-415: 当前用户是否可归还账户（对称于 getPassword 的联系人豁免范式）。
     *
     * <p>特权角色直接放行；投标专员要求为该账户绑定联系人；其他角色不放行。
     * 与 {@code canViewPassword} 保持一致的授权语义。roleCode 由调用方
     * （Service 层）通过 EffectiveRoleResolver 解析后传入，保持 Policy 纯静态。</p>
     */
    public static boolean canReturnAccount(String roleCode, PlatformAccount account, User currentUser) {
        if (currentUser == null) return false;
        if (isPrivilegedRole(roleCode)) return true;
        if (isBidTeamRole(roleCode)) return PlatformAccountContactMatcher.isContactPerson(account, currentUser);
        return false;
    }

    /** CO-415: 校验归还权限，不放行则抛 IllegalStateException。 */
    public static void checkCanReturnAccount(String roleCode, PlatformAccount account, User currentUser) {
        if (!canReturnAccount(roleCode, account, currentUser)) {
            throw new IllegalStateException(
                "Only administrators or the account's contact person can return the account");
        }
    }

    /**
     * CO-416: 当前用户是否可编辑/管理账户（对称于 canReturnAccount 的授权范式）。
     *
     * <p>授权矩阵（与前端 accountActions.resolveAccountActions 对齐）：
     * <ul>
     *   <li>特权角色（admin/bid-teamleader/bidadmin）→ 放行</li>
     *   <li>投标专员（bid-Team）+ 该账户绑定联系人 → 放行</li>
     *   <li>投标专员非绑定联系人 → 拒绝</li>
     *   <li>其他角色 → 拒绝</li>
     * </ul>
     *
     * <p>修复背景：原 {@code PlatformAccountController.updateAccount} 使用
     * {@code @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")} 硬收紧，导致投标专员
     * 即使是绑定联系人也被 403 拦截（违反 §24 权限矩阵对称设计原则）。
     * 现改为类级 {@code hasAuthority('resource')} + Service 层 Policy 校验，
     * 对称于 {@code getPassword}、{@code returnAccount} 的成熟范式。</p>
     */
    public static boolean canManageAccount(String roleCode, PlatformAccount account, User currentUser) {
        if (currentUser == null) return false;
        if (isPrivilegedRole(roleCode)) return true;
        if (isBidTeamRole(roleCode)) return PlatformAccountContactMatcher.isContactPerson(account, currentUser);
        return false;
    }

    /** CO-416: 校验账户管理权限，不放行则抛 AccessDeniedException。 */
    public static void checkCanManageAccount(String roleCode, PlatformAccount account, User currentUser) {
        if (!canManageAccount(roleCode, account, currentUser)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "仅管理员或账户绑定联系人可编辑账户");
        }
    }

    /**
     * 是否可创建平台账户。
     *
     * <p>授权矩阵：
     * <ul>
     *   <li>特权角色（admin/bid-teamleader/bidadmin）→ 放行</li>
     *   <li>白名单用户（由 system_settings 配置）→ 放行</li>
     *   <li>其他 → 拒绝</li>
     * </ul>
     *
     * @param roleCode 当前用户角色码（由 EffectiveRoleResolver 解析）
     * @param currentUser 当前登录用户
     * @param whitelistUsernames 白名单用户名列表（由 AccountCreationWhitelistStore 加载）
     */
    public static boolean canCreateAccount(String roleCode, User currentUser, List<String> whitelistUsernames) {
        return isPrivilegedOrWhitelisted(roleCode, currentUser, whitelistUsernames);
    }

    /** 校验创建账户权限，不放行则抛 AccessDeniedException。 */
    public static void checkCanCreateAccount(String roleCode, User currentUser, List<String> whitelistUsernames) {
        if (!canCreateAccount(roleCode, currentUser, whitelistUsernames)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "仅管理员或白名单用户可创建平台账户");
        }
    }

    /**
     * 是否可导出平台账户台账。
     *
     * <p>授权矩阵：
     * <ul>
     *   <li>特权角色（admin/bid-teamleader/bidadmin）→ 放行</li>
     *   <li>白名单用户（由 system_settings 配置）→ 放行</li>
     *   <li>其他 → 拒绝</li>
     * </ul>
     *
     * @param roleCode 当前用户角色码（由 EffectiveRoleResolver 解析）
     * @param currentUser 当前登录用户
     * @param whitelistUsernames 白名单用户名列表（由 AccountExportWhitelistStore 加载）
     */
    public static boolean canExportAccount(String roleCode, User currentUser, List<String> whitelistUsernames) {
        return isPrivilegedOrWhitelisted(roleCode, currentUser, whitelistUsernames);
    }

    /** 校验导出权限，不放行则抛 AccessDeniedException。 */
    public static void checkCanExportAccount(String roleCode, User currentUser, List<String> whitelistUsernames) {
        if (!canExportAccount(roleCode, currentUser, whitelistUsernames)) {
            throw new org.springframework.security.access.AccessDeniedException(
                "仅管理员或白名单用户可导出平台账户台账");
        }
    }

    /**
     * 通用白名单授权：特权角色 OR 白名单用户放行。
     *
     * <p>由 {@link #canCreateAccount} 和 {@link #canExportAccount} 复用，
     * 避免方法体重复。</p>
     */
    private static boolean isPrivilegedOrWhitelisted(String roleCode, User currentUser, List<String> whitelistUsernames) {
        if (currentUser == null) return false;
        if (isPrivilegedRole(roleCode)) return true;
        String username = currentUser.getUsername();
        return username != null && whitelistUsernames != null && whitelistUsernames.contains(username);
    }
}
