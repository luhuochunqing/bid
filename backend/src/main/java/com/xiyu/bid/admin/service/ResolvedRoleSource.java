package com.xiyu.bid.admin.service;

import java.util.List;
import java.util.Optional;

/**
 * 用户的角色/权限来源解析结果。
 * <p>统一封装 {@link DataScopeConfigService} 中 getRoleCode / getRoleName / getRoleMenuPermissions
 * 重复的缓存读取 + 本地账户判定逻辑。
 *
 * @param cachedRoleCode      OSS 缓存中的角色码（cache miss 时为 empty）
 * @param cachedMenuPermissions OSS 缓存中的菜单权限列表（cache miss 时为 empty）
 * @param localSystemAccount  是否为本地 admin 系统内置账户（允许 DB fallback）
 */
record ResolvedRoleSource(
        Optional<String> cachedRoleCode,
        Optional<List<String>> cachedMenuPermissions,
        boolean localSystemAccount
) {
}
