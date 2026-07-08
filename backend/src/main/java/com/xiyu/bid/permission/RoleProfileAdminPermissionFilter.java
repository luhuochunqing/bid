package com.xiyu.bid.permission;

import com.xiyu.bid.entity.RoleProfileCatalog;

import java.util.List;
import java.util.Set;

/**
 * 过滤 OSS 用户不应持有的内部 admin 专属权限键。
 * <p>OSS 同步用户的权限唯一来源是 OSS 菜单映射，不应继承本地系统管理员高权限键
 * （如 {@code "all"}），避免 OSS 侧配置疏漏导致权限扩散。
 * <p>注意：{@code system.admin} 和 {@code warehouse.manage} 曾被视为 admin 专属，
 * 但业务上 OSS 投标系统管理员需要访问系统设置与仓库功能，故不再过滤；
 * 由 OSS 端决定是否授予这些权限键（通过 1010/100408 等菜单授权）。
 */
public final class RoleProfileAdminPermissionFilter {

    private RoleProfileAdminPermissionFilter() {
    }

    /**
     * 本地系统管理员专属权限键集合（仅 all，不包含 system.admin/warehouse.manage）。
     */
    private static final Set<String> ADMIN_ONLY_PERMISSION_KEYS = Set.of(
            "all"
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
