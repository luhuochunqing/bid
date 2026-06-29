package com.xiyu.bid.platform.service;

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
}
