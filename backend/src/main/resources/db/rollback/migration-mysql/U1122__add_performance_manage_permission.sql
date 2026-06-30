-- Rollback V1122: 移除 performance.manage 权限点
-- Input: V1122__add_performance_manage_permission.sql
-- 操作类型：数据回滚（UPDATE roles.menu_permissions）
-- 影响范围：bid-TeamLeader / /bidAdmin / bid-Team 三个角色的 menu_permissions 字段
UPDATE roles
SET menu_permissions = REPLACE(menu_permissions, ',"performance.manage"', '')
WHERE code IN ('bid-TeamLeader', '/bidAdmin', 'bid-Team');
