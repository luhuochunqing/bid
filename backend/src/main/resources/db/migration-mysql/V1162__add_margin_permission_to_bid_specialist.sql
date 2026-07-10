-- CO-515: 为 bid-Team 角色追加 resource-margin 菜单权限
-- 让投标专员能访问资源管理 → 保证金管理（全量可见）
-- 采用幂等追加模式（V1012/V1109 风格），不覆盖运维通过前端手动调整的其他权限
-- 关联：RoleProfileCatalog.BID_SPECIALIST_CODE SeedDefinition.menuPermissions 同步追加

UPDATE roles
SET menu_permissions = CONCAT(menu_permissions, ',resource-margin'),
    updated_at = NOW()
WHERE code = 'bid-Team'
  AND menu_permissions NOT LIKE '%resource-margin%';
