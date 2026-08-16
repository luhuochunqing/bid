-- Input: migration-mysql/V1189__add_analytics_dashboard_permission_to_global_roles.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U1189: 回滚 add_analytics_dashboard_permission_to_global_roles
-- 回滚 V1189：从 /bidAdmin、bid-TeamLeader、bid-SystemAdmin 移除 analytics-dashboard 权限点。
-- 前置防御：仅更新非空 menu_permissions 且确实包含该权限的行，避免误改。

UPDATE roles
SET menu_permissions = TRIM(BOTH ',' FROM
    REPLACE(
        REPLACE(CONCAT(',', menu_permissions, ','), ',analytics-dashboard,', ','),
        ',,', ','
    )
),
updated_at = NOW()
WHERE code IN ('/bidAdmin', 'bid-TeamLeader', 'bid-SystemAdmin')
  AND menu_permissions IS NOT NULL AND menu_permissions != ''
  AND FIND_IN_SET('analytics-dashboard', menu_permissions) > 0;
