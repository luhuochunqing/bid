-- CO-394-B: 为投标组长(bid-TeamLeader)、投标管理员(/bidAdmin)、投标专员(bid-Team)
-- 新增 personnel.manage 权限点，用于 @PreAuthorize(hasAuthority) 鉴权
-- 替代之前硬编码 roleCode 白名单 hasAnyAuthority('admin','/bidAdmin','bid-TeamLeader','bid-Team')
-- 以及混合 ROLE_ 前缀的 hasAnyAuthority(...,'ROLE_BIDADMIN','ROLE_BID_TEAMLEADER',...) 写法
--
-- 权限点语义：人员库管理（新增/编辑/删除/恢复/导入等写操作）
-- 三角色一致性对齐：投标专员(bid-Team) 与组长/管理员获得相同的写操作权限
-- 注：personnel.view（只读）已在 V1118 授予，本次仅追加 manage（写）

-- 1. bid-TeamLeader
UPDATE roles
SET menu_permissions = CASE
    WHEN menu_permissions LIKE '%personnel.manage%' THEN menu_permissions
    ELSE CONCAT(menu_permissions, ',"personnel.manage"')
END
WHERE code = 'bid-TeamLeader';

-- 2. /bidAdmin
UPDATE roles
SET menu_permissions = CASE
    WHEN menu_permissions LIKE '%personnel.manage%' THEN menu_permissions
    ELSE CONCAT(menu_permissions, ',"personnel.manage"')
END
WHERE code = '/bidAdmin';

-- 3. bid-Team
UPDATE roles
SET menu_permissions = CASE
    WHEN menu_permissions LIKE '%personnel.manage%' THEN menu_permissions
    ELSE CONCAT(menu_permissions, ',"personnel.manage"')
END
WHERE code = 'bid-Team';

-- admin 角色拥有 'all' 权限，运行时动态展开所有 seedDefinitions 的 menuPermissions，无需修改
