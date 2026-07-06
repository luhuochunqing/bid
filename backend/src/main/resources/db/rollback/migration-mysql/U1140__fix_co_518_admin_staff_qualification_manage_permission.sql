-- Input: V1140__fix_co_518_admin_staff_qualification_manage_permission.sql
-- CO-518 回滚：移除行政人员(bid-administration)的 qualification.manage 权限
-- 注意：回滚后行政人员无法执行资质证书写操作(新增/编辑/删除/上传/AI解析)
--       仅保留 qualification.view 只读权限

UPDATE roles
SET menu_permissions = TRIM(BOTH ',' FROM REPLACE(CONCAT(',', menu_permissions, ','), ',qualification.manage,', ','))
WHERE code = 'bid-administration';
