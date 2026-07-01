package com.xiyu.bid.resources.service;

import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.resources.entity.CaBorrowApplicationEntity;
import lombok.extern.slf4j.Slf4j;

/**
 * CO-459: CA 借用申请权限校验器（静态工具类，无需 Spring 注入）。
 * 规则：
 * - 管理员角色（admin / bidAdmin / bid-TeamLeader）可操作任意申请
 * - CA保管员（approverId）可操作对应申请
 * - 其他用户无权操作
 */
@Slf4j
public final class CaBorrowPermissionChecker {

    private CaBorrowPermissionChecker() {}

    /**
     * 校验当前用户是否有权审批/归还 CA 借用申请。
     * @throws CaBusinessException 如果用户无权限
     */
    public static void requireCustodianOrPrivilegedRole(CaBorrowApplicationEntity app, User currentUser, String roleCode) {
        if (currentUser == null) {
            throw new CaBusinessException("用户未登录");
        }
        if (isPrivilegedRole(roleCode)) {
            log.info("管理员 {} 跨权限操作CA借用申请 {}", currentUser.getId(), app.getId());
            return;
        }
        if (app.getApproverId() == null || !currentUser.getId().equals(app.getApproverId())) {
            throw new CaBusinessException("无审批权限");
        }
    }

    /**
     * 判断用户是否为管理员角色（可查看全部申请）。
     */
    public static boolean isPrivilegedRole(String roleCode) {
        return RoleProfileCatalog.GLOBAL_ACCESS_ROLES.contains(roleCode);
    }
}
