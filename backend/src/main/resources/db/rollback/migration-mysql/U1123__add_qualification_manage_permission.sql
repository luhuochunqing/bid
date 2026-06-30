-- Rollback V1123: 移除 qualification.manage 权限点
-- Input: V1123__add_qualification_manage_permission.sql
-- 操作类型：数据回滚（UPDATE roles.menu_permissions）
-- 影响范围：bid-TeamLeader / /bidAdmin / bid-Team 三个角色的 menu_permissions 字段
UPDATE roles
SET menu_permissions = REPLACE(menu_permissions, ',"qualification.manage"', '')
WHERE code IN ('bid-TeamLeader', '/bidAdmin', 'bid-Team');
