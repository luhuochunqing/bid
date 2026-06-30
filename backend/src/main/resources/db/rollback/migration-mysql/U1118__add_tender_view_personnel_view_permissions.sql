-- Rollback V1118: 移除 tender.view 和 personnel.view 权限点
-- Input: V1118__add_tender_view_personnel_view_permissions.sql
-- 操作类型：数据回滚（UPDATE roles.menu_permissions）
-- 影响范围：bid-projectLeader / bid-TeamLeader / /bidAdmin / bid-Team 四个角色的 menu_permissions 字段
UPDATE roles
SET menu_permissions = REPLACE(REPLACE(menu_permissions, ',"tender.view"', ''), ',"personnel.view"', '')
WHERE code IN ('bid-projectLeader', 'bid-TeamLeader', '/bidAdmin', 'bid-Team');
