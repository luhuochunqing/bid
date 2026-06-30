-- CO-403: 为投标项目负责人(bid-projectLeader)、投标组长(bid-TeamLeader)、投标管理员(/bidAdmin)、投标专员(bid-Team)
-- 新增 tender.view 和 personnel.view 权限点，用于 @PreAuthorize(hasAuthority) 鉴权
-- 替代之前硬编码 roleCode 白名单的写法

-- 1. bid-projectLeader
UPDATE roles
SET menu_permissions = CASE
    WHEN menu_permissions LIKE '%tender.view%' THEN menu_permissions
    ELSE CONCAT(menu_permissions, ',"tender.view"')
END
WHERE code = 'bid-projectLeader';

UPDATE roles
SET menu_permissions = CASE
    WHEN menu_permissions LIKE '%personnel.view%' THEN menu_permissions
    ELSE CONCAT(menu_permissions, ',"personnel.view"')
END
WHERE code = 'bid-projectLeader';

-- 2. bid-TeamLeader
UPDATE roles
SET menu_permissions = CASE
    WHEN menu_permissions LIKE '%tender.view%' THEN menu_permissions
    ELSE CONCAT(menu_permissions, ',"tender.view"')
END
WHERE code = 'bid-TeamLeader';

UPDATE roles
SET menu_permissions = CASE
    WHEN menu_permissions LIKE '%personnel.view%' THEN menu_permissions
    ELSE CONCAT(menu_permissions, ',"personnel.view"')
END
WHERE code = 'bid-TeamLeader';

-- 3. /bidAdmin
UPDATE roles
SET menu_permissions = CASE
    WHEN menu_permissions LIKE '%tender.view%' THEN menu_permissions
    ELSE CONCAT(menu_permissions, ',"tender.view"')
END
WHERE code = '/bidAdmin';

UPDATE roles
SET menu_permissions = CASE
    WHEN menu_permissions LIKE '%personnel.view%' THEN menu_permissions
    ELSE CONCAT(menu_permissions, ',"personnel.view"')
END
WHERE code = '/bidAdmin';

-- 4. bid-Team
UPDATE roles
SET menu_permissions = CASE
    WHEN menu_permissions LIKE '%tender.view%' THEN menu_permissions
    ELSE CONCAT(menu_permissions, ',"tender.view"')
END
WHERE code = 'bid-Team';

UPDATE roles
SET menu_permissions = CASE
    WHEN menu_permissions LIKE '%personnel.view%' THEN menu_permissions
    ELSE CONCAT(menu_permissions, ',"personnel.view"')
END
WHERE code = 'bid-Team';

-- admin 角色拥有 'all' 权限，无需修改
