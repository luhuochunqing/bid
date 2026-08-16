-- 数据分析页面（/analytics/dashboard）权限收紧：仅为 3 个全局角色补齐 analytics-dashboard 权限点
-- 背景：PR 2295 数据分析重构将控制器鉴权放宽为 isAuthenticated()/dashboard，造成越权风险。
--      修复口径（P0-2）：数据分析页面仅对 投标系统管理员(bid-SystemAdmin) /
--      投标管理员(/bidAdmin) / 投标组长(bid-TeamLeader) 三个全局角色开放；
--      admin 经 List.of("all") 动态展开天然覆盖，无需迁移。
-- 根因：本地 DB roles.menu_permissions 为权威数据源（catalog fallback 仅在为空时生效），
--      RoleProfileCatalog 已同步添加 ANALYTICS_DASHBOARD_PERMISSION，但存量角色需迁移补齐。
-- 防御：仅在 menu_permissions 不包含目标权限时追加，避免重复。
-- 注：bid-Team（投标专员）/ bid-projectLeader（项目负责人）/ bid-administration / bid-otherDept
--     不在授权范围内，不补权限；对应后端接口已统一改为 hasAuthority('analytics-dashboard')。

UPDATE roles
SET menu_permissions = CONCAT(TRIM(BOTH ',' FROM menu_permissions), ',analytics-dashboard'),
    updated_at = NOW()
WHERE code IN ('/bidAdmin', 'bid-TeamLeader', 'bid-SystemAdmin')
  AND menu_permissions IS NOT NULL AND menu_permissions != ''
  AND FIND_IN_SET('analytics-dashboard', menu_permissions) = 0;
