-- CO-394-D: 为投标组长(bid-TeamLeader)、投标管理员(/bidAdmin)、投标专员(bid-Team)
-- 新增 qualification.manage 权限点，用于 @PreAuthorize(hasAuthority) 鉴权
-- 替代之前 hasAnyRole('ADMIN','BID_ADMINISTRATION','BIDADMIN','BID_TEAMLEADER',...) 混合白名单
-- 修正 BID_ADMINISTRATION 错名（实际是 bid-administration 行政人员，不应有资质写入权限）
-- 修正 QualificationExportController 中 BIDADMIN 重复 bug
--
-- 权限点语义：资质证书管理（读/写/导入/导出/扫描统一为单一权限点，对齐 Warehouse 模板）
-- 三角色一致性对齐：投标专员(bid-Team) 与组长/管理员获得相同的资质管理权限
-- 注：行政人员(bid-administration) 仅有 qualification.view（只读），不含 manage

-- 1. bid-TeamLeader
UPDATE roles
SET menu_permissions = CASE
    WHEN menu_permissions LIKE '%qualification.manage%' THEN menu_permissions
    ELSE CONCAT(menu_permissions, ',"qualification.manage"')
END
WHERE code = 'bid-TeamLeader';

-- 2. /bidAdmin
UPDATE roles
SET menu_permissions = CASE
    WHEN menu_permissions LIKE '%qualification.manage%' THEN menu_permissions
    ELSE CONCAT(menu_permissions, ',"qualification.manage"')
END
WHERE code = '/bidAdmin';

-- 3. bid-Team
UPDATE roles
SET menu_permissions = CASE
    WHEN menu_permissions LIKE '%qualification.manage%' THEN menu_permissions
    ELSE CONCAT(menu_permissions, ',"qualification.manage"')
END
WHERE code = 'bid-Team';

-- admin 角色拥有 'all' 权限，运行时动态展开所有 seedDefinitions 的 menuPermissions，无需修改
-- 行政人员(bid-administration) 仅有 qualification.view（只读），不追加 manage
