-- CO-394-C: 为投标组长(bid-TeamLeader)、投标管理员(/bidAdmin)、投标专员(bid-Team)
-- 新增 performance.manage 权限点，用于 @PreAuthorize(hasAuthority) 鉴权
-- 替代之前 hasAnyRole('ADMIN','MANAGER') legacy role 白名单
-- 以及读端点 isAuthenticated() 过宽权限（任何登录用户可读）
--
-- 权限点语义：业绩管理（读/写/导入/导出统一为单一权限点，对齐 Warehouse 模板）
-- 三角色一致性对齐：投标专员(bid-Team) 与组长/管理员获得相同的业绩管理权限

-- 1. bid-TeamLeader
UPDATE roles
SET menu_permissions = CASE
    WHEN menu_permissions LIKE '%performance.manage%' THEN menu_permissions
    ELSE CONCAT(menu_permissions, ',"performance.manage"')
END
WHERE code = 'bid-TeamLeader';

-- 2. /bidAdmin
UPDATE roles
SET menu_permissions = CASE
    WHEN menu_permissions LIKE '%performance.manage%' THEN menu_permissions
    ELSE CONCAT(menu_permissions, ',"performance.manage"')
END
WHERE code = '/bidAdmin';

-- 3. bid-Team
UPDATE roles
SET menu_permissions = CASE
    WHEN menu_permissions LIKE '%performance.manage%' THEN menu_permissions
    ELSE CONCAT(menu_permissions, ',"performance.manage"')
END
WHERE code = 'bid-Team';

-- admin 角色拥有 'all' 权限，运行时动态展开所有 seedDefinitions 的 menuPermissions，无需修改
