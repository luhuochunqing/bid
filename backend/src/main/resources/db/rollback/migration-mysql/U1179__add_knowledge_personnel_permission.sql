-- Input: migration-mysql/V1179__add_knowledge_personnel_permission.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U1179: 回滚 add_knowledge_personnel_permission
-- 回滚 V1179：从 /bidAdmin、bid-TeamLeader、bid-Team、bid-SystemAdmin 移除 knowledge-personnel 权限
-- 注意：回滚会导致上述角色无法访问 /knowledge/personnel 路由，仅在确有需要时执行。

UPDATE roles
SET menu_permissions = TRIM(BOTH ',' FROM REPLACE(
    REPLACE(CONCAT(',', menu_permissions, ','), ',knowledge-personnel,', ','),
    ',,', ','
)),
updated_at = NOW()
WHERE code IN ('/bidAdmin', 'bid-TeamLeader', 'bid-Team', 'bid-SystemAdmin')
  AND FIND_IN_SET('knowledge-personnel', menu_permissions) > 0;
