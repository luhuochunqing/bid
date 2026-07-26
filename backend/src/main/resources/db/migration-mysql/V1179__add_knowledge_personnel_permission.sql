-- 修复：给 /bidAdmin、bid-TeamLeader、bid-Team、bid-SystemAdmin 角色补全 knowledge-personnel 菜单权限
-- 根因：前端路由 /knowledge/personnel 配置 permissionKeys=['knowledge','knowledge-personnel']，
--      路由守卫采用 every 校验，要求用户同时持有 knowledge 和 knowledge-personnel 才能通过。
--      但 RoleProfileCatalog 中上述角色虽持有 personnel.view / personnel.manage 操作权限，
--      却未配置 knowledge-personnel 菜单权限，导致这些角色登录后被路由守卫拦截重定向到工作台，
--      无法访问"人员证书"页面（影响投标专员录入人员教育经历/证书等核心流程）。
--
-- 影响角色：/bidAdmin / bid-TeamLeader / bid-Team / bid-SystemAdmin
-- 同步修复：RoleProfileCatalog 已为上述角色加上 KNOWLEDGE_PERSONNEL_PERMISSION 常量。
-- 防御：仅在 menu_permissions 不包含 knowledge-personnel 时追加，避免重复。
-- 注：bid-projectLeader 仅持有 personnel.view（只读），其前端访问由后端鉴权约束，
--     本次不修改其菜单权限以保持蓝图"投标编制时只读访问人员库"语义。

UPDATE roles
SET menu_permissions = CONCAT(
    TRIM(BOTH ',' FROM menu_permissions),
    ',knowledge-personnel'
),
updated_at = NOW()
WHERE code IN ('/bidAdmin', 'bid-TeamLeader', 'bid-Team', 'bid-SystemAdmin')
  AND menu_permissions IS NOT NULL
  AND menu_permissions != ''
  AND FIND_IN_SET('knowledge-personnel', menu_permissions) = 0;
