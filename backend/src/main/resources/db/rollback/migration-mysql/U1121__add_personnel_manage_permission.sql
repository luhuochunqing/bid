-- Rollback V1121: 移除 personnel.manage 权限点
-- Input: V1121__add_personnel_manage_permission.sql
-- 操作类型：数据回滚（UPDATE roles.menu_permissions）
-- 影响范围：bid-TeamLeader / /bidAdmin / bid-Team 三个角色的 menu_permissions 字段
UPDATE roles
SET menu_permissions = REPLACE(menu_permissions, ',"personnel.manage"', '')
WHERE code IN ('bid-TeamLeader', '/bidAdmin', 'bid-Team');
