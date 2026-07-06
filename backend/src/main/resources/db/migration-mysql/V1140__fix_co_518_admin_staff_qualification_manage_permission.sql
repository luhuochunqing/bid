-- CO-518: 为行政人员(bid-administration)补充 qualification.manage 权限
-- 根因：行政人员角色定位是"资质证书管理"，但只有 qualification.view 只读权限，
--       导致上传资质证书附件(AI解析)、新增/编辑/删除资质等写操作返回 403。
-- 修复：追加 qualification.manage 到 menu_permissions。

UPDATE roles
SET menu_permissions = CASE
    WHEN menu_permissions LIKE '%qualification.manage%' THEN menu_permissions
    ELSE CONCAT(menu_permissions, ',qualification.manage')
END
WHERE code = 'bid-administration';
