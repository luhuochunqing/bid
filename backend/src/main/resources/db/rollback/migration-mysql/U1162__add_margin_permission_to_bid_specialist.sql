-- Input: V1162__add_margin_permission_to_bid_specialist.sql
-- 回滚 V1162：移除 bid-Team 的 resource-margin 菜单权限
-- 采用幂等移除模式，不覆盖运维通过前端手动调整的其他权限

UPDATE roles
SET menu_permissions = TRIM(BOTH ',' FROM REPLACE(CONCAT(',', menu_permissions, ','), ',resource-margin,', ',')),
    updated_at = NOW()
WHERE code = 'bid-Team'
  AND menu_permissions LIKE '%resource-margin%';
