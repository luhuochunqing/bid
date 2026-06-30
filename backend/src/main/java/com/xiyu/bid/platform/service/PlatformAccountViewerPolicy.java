package com.xiyu.bid.platform.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.platform.entity.PlatformAccount;
import com.xiyu.bid.platform.util.PlatformAccountContactMatcher;
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
}
