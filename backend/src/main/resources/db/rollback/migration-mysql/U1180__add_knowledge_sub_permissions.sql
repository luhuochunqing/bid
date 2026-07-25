-- Input: migration-mysql/V1180__add_knowledge_sub_permissions.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U1180: 回滚 add_knowledge_sub_permissions
-- 回滚 V1180：从 /bidAdmin、bid-TeamLeader、bid-Team、bid-SystemAdmin 移除
-- knowledge-archive, knowledge-case, knowledge-template, knowledge-warehouse, knowledge-performance

UPDATE roles
SET menu_permissions = TRIM(BOTH ',' FROM
    REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
        REPLACE(CONCAT(',', menu_permissions, ','),
            ',knowledge-archive,', ','),
            ',knowledge-case,', ','),
            ',knowledge-template,', ','),
            ',knowledge-warehouse,', ','),
            ',knowledge-performance,', ','),
        ',,', ',')
),
updated_at = NOW()
WHERE code IN ('/bidAdmin', 'bid-TeamLeader', 'bid-Team', 'bid-SystemAdmin');
