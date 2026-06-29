-- CO-393: 为 bid-projectLeader 角色追加 resource / resource-account / resource-ca 菜单权限
-- 让项目负责人能访问资源管理 → 账户管理 / CA信息管理（只读简化视图）
-- 采用幂等追加模式（V1012 风格），不覆盖运维通过前端手动调整的其他权限
-- 关联：RoleProfileCatalog.SALES_CODE SeedDefinition.menuPermissions 同步追加

UPDATE roles
SET menu_permissions = CONCAT(menu_permissions, ',resource'),
    updated_at = NOW()
WHERE code = 'bid-projectLeader'
  AND menu_permissions NOT LIKE '%,resource,%'
  AND menu_permissions NOT LIKE 'resource,%'
  AND menu_permissions NOT LIKE '%,resource'
  AND menu_permissions <> 'resource';

UPDATE roles
SET menu_permissions = CONCAT(menu_permissions, ',resource-account'),
    updated_at = NOW()
WHERE code = 'bid-projectLeader'
  AND menu_permissions NOT LIKE '%resource-account%';

UPDATE roles
SET menu_permissions = CONCAT(menu_permissions, ',resource-ca'),
    updated_at = NOW()
WHERE code = 'bid-projectLeader'
  AND menu_permissions NOT LIKE '%resource-ca%';
