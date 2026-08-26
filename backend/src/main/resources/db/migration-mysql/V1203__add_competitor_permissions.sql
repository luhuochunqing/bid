-- V1203: 竞品字典权限点——competitor.create / competitor.govern
--
-- 背景：specs/055 竞品公司自定义输入与竞争对手库治理（方案 B1）。
--   competitor.create：竞品创建/upsert，授予投标业务角色（bid-Team / bid-TeamLeader / bid-projectLeader）
--   competitor.govern：竞品治理（改名/合并/停用），授予治理角色（/bidAdmin / bid-TeamLeader / bid-SystemAdmin）
-- 注：/bidAdmin 与 bid-SystemAdmin 同时持有两个权限；admin 经 List.of("all") 动态展开，无需迁移。
-- 写法对齐 V1189（无字面双引号 + FIND_IN_SET 防重；V1121 的引号写法已被 V1174 修复）。
-- 防御：仅在 menu_permissions 不包含目标权限时追加，避免重复。

-- 1. competitor.create → 投标业务角色
UPDATE roles
SET menu_permissions = CONCAT(TRIM(BOTH ',' FROM menu_permissions), ',competitor.create'),
    updated_at = NOW()
WHERE code IN ('bid-Team', 'bid-TeamLeader', 'bid-projectLeader', '/bidAdmin', 'bid-SystemAdmin')
  AND menu_permissions IS NOT NULL AND menu_permissions != ''
  AND FIND_IN_SET('competitor.create', menu_permissions) = 0;

-- 2. competitor.govern → 治理角色
UPDATE roles
SET menu_permissions = CONCAT(TRIM(BOTH ',' FROM menu_permissions), ',competitor.govern'),
    updated_at = NOW()
WHERE code IN ('/bidAdmin', 'bid-TeamLeader', 'bid-SystemAdmin')
  AND menu_permissions IS NOT NULL AND menu_permissions != ''
  AND FIND_IN_SET('competitor.govern', menu_permissions) = 0;
