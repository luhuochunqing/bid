package com.xiyu.bid.tender.core;

import java.util.Locale;
import java.util.Set;

/**
 * 标讯转派权限的纯核心策略。
 *
 * <p>仅做值到值的规则计算：给定用户角色码，返回是否允许执行标讯转派。
 * 无 IO、无 Spring 注入、不写状态 — 真单元策略。
 *
 * <p>权限矩阵来源：飞书标讯权限矩阵（2026-06-17）。
 * 允许转派的角色：admin / bid_admin / bid_lead / bid_senior。
 */
public final class TenderTransferPermissionPolicy {

    private TenderTransferPermissionPolicy() {
        // 工具类不可实例化
    }

    private static final Set<String> TRANSFERABLE_ROLES = Set.of(
            "admin",
            "/bidadmin",
            "bid-teamleader"
    );

    /**
     * 判定给定角色是否允许执行标讯转派。
     *
     * @param roleCode 用户角色码；null/空白/未注册角色均按无权限处理
     * @return true 表示允许转派
     */
    public static boolean canTransfer(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return false;
        }
        return TRANSFERABLE_ROLES.contains(roleCode.trim().toLowerCase(Locale.ROOT));
    }
}
