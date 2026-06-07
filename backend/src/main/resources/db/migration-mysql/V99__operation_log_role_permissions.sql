UPDATE roles
SET menu_permissions = CASE
    WHEN menu_permissions IS NULL OR TRIM(menu_permissions) = '' THEN 'operation-logs'
    ELSE CONCAT(menu_permissions, ',operation-logs')
END
WHERE LOWER(code) IN ('manager', 'staff', 'auditor')
  AND (menu_permissions IS NULL OR FIND_IN_SET('operation-logs', menu_permissions) = 0);

UPDATE roles
SET menu_permissions = CASE
    WHEN menu_permissions IS NULL OR TRIM(menu_permissions) = '' THEN 'audit-logs'
    ELSE CONCAT(menu_permissions, ',audit-logs')
END
WHERE LOWER(code) = 'auditor'
  AND (menu_permissions IS NULL OR FIND_IN_SET('audit-logs', menu_permissions) = 0);
