-- 修复：移除 roles.menu_permissions 字段中错误包裹的字面双引号
-- 根因：V1118/V1121/V1122/V1123 迁移使用了 CONCAT(menu_permissions, ',"tender.view"')
-- 这种写法把字面双引号 " 也写入了字段值，导致存储为 "tender.view"（带引号），
-- 而非期望的 tender.view。前端 perms.includes('tender.view') 不匹配 '"tender.view"'，
-- 后端 hasAuthority('tender.view') 也失败，造成权限校验失效。
--
-- 影响权限：tender.view / personnel.view / personnel.manage /
--          performance.manage / qualification.manage
-- 影响角色：bid-projectLeader / bid-TeamLeader / /bidAdmin / bid-SystemAdmin / bid-Team
--
-- 修复策略：用 REPLACE 移除字段中所有字面双引号（权限名不允许包含双引号，
--          所以可以安全移除）。
-- 同步修复：RoleProfile.splitStrings 已加防御性去引号逻辑（运行时兜底）。

UPDATE roles
SET menu_permissions = REPLACE(menu_permissions, '"', '')
WHERE menu_permissions IS NOT NULL
  AND menu_permissions LIKE '%"%"';
