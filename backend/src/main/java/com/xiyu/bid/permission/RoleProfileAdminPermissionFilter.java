package com.xiyu.bid.permission;

import com.xiyu.bid.entity.RoleProfileCatalog;

import java.util.List;
import java.util.Set;

/**
 * 过滤 OSS 用户不应持有的内部 admin 专属权限键。
 * <p>OSS 同步用户的权限唯一来源是 OSS 菜单映射，不应继承本地系统管理员高权限键
 * （如 {@code "all"}、{@code "system.admin"}、{@code "warehouse.manage"}），
 * 避免 OSS 侧配置疏漏导致权限扩散。
 */
public final class RoleProfileAdminPermissionFilter {

    private RoleProfileAdminPermissionFilter() {
    }

    /**
     * 本地系统管理员专属权限键集合。
     */
    private static final Set<String> ADMIN_ONLY_PERMISSION_KEYS = Set.of(
            "all",
            RoleProfileCatalog.SYSTEM_ADMIN_PERMISSION,
            RoleProfileCatalog.WAREHOUSE_MANAGE_PERMISSION
    );

    /**
     * 过滤掉 OSS 用户不应持有的内部 admin 专属权限键。
     *
     * @param permissions 原始权限列表（可为 null）
     * @return 不含 admin 专属权限键的新列表
     */
    public static List<String> filter(List<String> permissions) {
        return normalize(permissions).stream()
                .filter(p -> !ADMIN_ONLY_PERMISSION_KEYS.contains(p))
                .toList();
    }

    /**
     * 规范化权限列表：去 null/blank、trim、去重，保持原有顺序。
     *
     * @param permissions 原始权限列表（可为 null）
     * @return 规范化后的新列表
     */
    public static List<String> normalize(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return List.of();
        }
        return permissions.stream()
                .filter(permission -> permission != null && !permission.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
