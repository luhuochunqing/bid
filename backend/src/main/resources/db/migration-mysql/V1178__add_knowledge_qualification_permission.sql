-- 修复：给 /bidAdmin、bid-TeamLeader、bid-Team、bid-SystemAdmin 角色补全 knowledge-qualification 菜单权限
-- 根因：commit f21dce017 把前端路由守卫从 some 改为 every 后，
--      /knowledge/qualification 路由的 permissionKeys=['knowledge','knowledge-qualification']
--      要求用户同时持有 knowledge 和 knowledge-qualification 才能通过。
--      但 RoleProfileCatalog 中 /bidAdmin、bid-TeamLeader、bid-Team、bid-SystemAdmin
--      虽持有 qualification.manage 操作权限，却未配置 knowledge-qualification 菜单权限，
--      导致这些角色登录后被路由守卫拦截重定向到工作台，无法访问资质证书页面。
--
-- 影响角色：/bidAdmin / bid-TeamLeader / bid-Team / bid-SystemAdmin
-- 同步修复：RoleProfileCatalog 已为上述角色加上 KNOWLEDGE_QUALIFICATION_PERMISSION 常量。
-- 防御：仅在 menu_permissions 不包含 knowledge-qualification 时追加，避免重复。

UPDATE roles
SET menu_permissions = CONCAT(
    TRIM(BOTH ',' FROM menu_permissions),
    ',knowledge-qualification'
),
updated_at = NOW()
WHERE code IN ('/bidAdmin', 'bid-TeamLeader', 'bid-Team', 'bid-SystemAdmin')
  AND menu_permissions IS NOT NULL
  AND menu_permissions != ''
  AND FIND_IN_SET('knowledge-qualification', menu_permissions) = 0;
