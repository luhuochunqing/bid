-- Input: migration-mysql/V1178__add_knowledge_qualification_permission.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U1178: 回滚 add_knowledge_qualification_permission
-- 回滚 V1178: 移除 V1178 通过 UPDATE roles.menu_permissions 追加的 'knowledge-qualification' 项
-- 注意：V1178 仅在 menu_permissions 末尾追加 ',knowledge-qualification'，
--      不影响原有的 qualification.manage 等权限。
-- 回滚策略：用 REGEXP_REPLACE 移除 'knowledge-qualification' 项（含可能的逗号）。

UPDATE roles
SET menu_permissions = TRIM(BOTH ',' FROM REGEXP_REPLACE(
    CONCAT(',', menu_permissions, ','),
    ',knowledge-qualification,',
    ','
)),
updated_at = NOW()
WHERE code IN ('/bidAdmin', 'bid-TeamLeader', 'bid-Team', 'bid-SystemAdmin')
  AND menu_permissions IS NOT NULL
  AND menu_permissions != ''
  AND FIND_IN_SET('knowledge-qualification', menu_permissions) > 0;
