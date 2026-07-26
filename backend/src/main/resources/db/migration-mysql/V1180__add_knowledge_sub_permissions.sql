-- 修复：给投标部门核心角色（/bidAdmin、bid-TeamLeader、bid-Team、bid-SystemAdmin）批量补全 knowledge-* 子菜单权限
-- 根因：前端路由 /knowledge/{archive,case,template,warehouse,performance} 配置
--      permissionKeys=['knowledge','knowledge-<sub>']，路由守卫 every 校验要求同时持有。
--      但 RoleProfileCatalog 中上述角色虽持有父权限 knowledge，却未配置这些子菜单权限，
--      导致角色登录后被路由守卫拦截重定向到工作台，无法访问对应页面。
--
-- 影响角色与补全权限：
--   /bidAdmin / bid-TeamLeader / bid-Team / bid-SystemAdmin
--     补：knowledge-archive, knowledge-case, knowledge-template,
--         knowledge-warehouse, knowledge-performance
--
-- 同步修复：RoleProfileCatalog 已为上述角色加上对应常量。
-- 防御：仅在 menu_permissions 不包含目标权限时追加，避免重复。
-- 注：bid-projectLeader 仅持有 personnel.view（只读），其菜单权限由蓝图 4.3 单独定义；
--     bid-administration 仅访问资质证书，本次不修改。

-- 1) knowledge-archive（项目档案）
UPDATE roles
SET menu_permissions = CONCAT(TRIM(BOTH ',' FROM menu_permissions), ',knowledge-archive'),
    updated_at = NOW()
WHERE code IN ('/bidAdmin', 'bid-TeamLeader', 'bid-Team', 'bid-SystemAdmin')
  AND menu_permissions IS NOT NULL AND menu_permissions != ''
  AND FIND_IN_SET('knowledge-archive', menu_permissions) = 0;

-- 2) knowledge-case（案例库）
UPDATE roles
SET menu_permissions = CONCAT(TRIM(BOTH ',' FROM menu_permissions), ',knowledge-case'),
    updated_at = NOW()
WHERE code IN ('/bidAdmin', 'bid-TeamLeader', 'bid-Team', 'bid-SystemAdmin')
  AND menu_permissions IS NOT NULL AND menu_permissions != ''
  AND FIND_IN_SET('knowledge-case', menu_permissions) = 0;

-- 3) knowledge-template（模板库）
UPDATE roles
SET menu_permissions = CONCAT(TRIM(BOTH ',' FROM menu_permissions), ',knowledge-template'),
    updated_at = NOW()
WHERE code IN ('/bidAdmin', 'bid-TeamLeader', 'bid-Team', 'bid-SystemAdmin')
  AND menu_permissions IS NOT NULL AND menu_permissions != ''
  AND FIND_IN_SET('knowledge-template', menu_permissions) = 0;

-- 4) knowledge-warehouse（仓库信息，操作层由 warehouse.manage 控制）
UPDATE roles
SET menu_permissions = CONCAT(TRIM(BOTH ',' FROM menu_permissions), ',knowledge-warehouse'),
    updated_at = NOW()
WHERE code IN ('/bidAdmin', 'bid-TeamLeader', 'bid-Team', 'bid-SystemAdmin')
  AND menu_permissions IS NOT NULL AND menu_permissions != ''
  AND FIND_IN_SET('knowledge-warehouse', menu_permissions) = 0;

-- 5) knowledge-performance（业绩管理，操作层由 performance.manage 控制）
UPDATE roles
SET menu_permissions = CONCAT(TRIM(BOTH ',' FROM menu_permissions), ',knowledge-performance'),
    updated_at = NOW()
WHERE code IN ('/bidAdmin', 'bid-TeamLeader', 'bid-Team', 'bid-SystemAdmin')
  AND menu_permissions IS NOT NULL AND menu_permissions != ''
  AND FIND_IN_SET('knowledge-performance', menu_permissions) = 0;
