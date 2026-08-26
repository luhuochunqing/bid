-- Input: 回滚脚本参数、当前 DB 状态
-- Output: 成功从指定角色 menu_permissions 移除 competitor.create / competitor.govern
-- Pos: 与 V1203 配对，作为竞品字典权限点的回滚
-- 维护声明: 维护者按项目SOP；与 V1203 一起提交，含 header 满足 FlywayRollbackScriptCoverageTest
-- Source: V1203__add_competitor_permissions.sql

-- U1203 rollback for V1203__add_competitor_permissions.sql（竞品创建/治理权限点）
-- 注意：移除权限点后，对应角色的竞品创建/治理接口将因 hasAuthority 失败返回 403。

-- 1. 移除 competitor.create
UPDATE roles
SET menu_permissions = TRIM(BOTH ',' FROM REPLACE(CONCAT(',', menu_permissions, ','), ',competitor.create,', ',')),
    updated_at = NOW()
WHERE code IN ('bid-Team', 'bid-TeamLeader', 'bid-projectLeader', '/bidAdmin', 'bid-SystemAdmin');

-- 2. 移除 competitor.govern
UPDATE roles
SET menu_permissions = TRIM(BOTH ',' FROM REPLACE(CONCAT(',', menu_permissions, ','), ',competitor.govern,', ',')),
    updated_at = NOW()
WHERE code IN ('/bidAdmin', 'bid-TeamLeader', 'bid-SystemAdmin');
