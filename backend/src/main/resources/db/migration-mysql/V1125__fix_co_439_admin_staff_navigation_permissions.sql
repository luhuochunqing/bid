-- CO-439: 为行政人员(bid-administration)补充前端导航权限
-- 根因：行政人员缺少 knowledge（父权限）和 knowledge-qualification（子路由权限），
--       导致前端路由守卫 hasAllPermissions(['knowledge','knowledge-qualification']) 拦截，
--       用户看到 403"权限不足"错误，请求根本未到达后端 API。
-- 后端 GET 端点已使用 qualification.view 权限（行政人员已有），无需修改。
--
-- 追加 knowledge 和 knowledge-qualification 到 menu_permissions。

UPDATE roles
SET menu_permissions = CASE
    WHEN menu_permissions LIKE '%knowledge-qualification%' THEN menu_permissions
    WHEN menu_permissions LIKE '%knowledge%' THEN CONCAT(menu_permissions, ',knowledge-qualification')
    ELSE CONCAT(menu_permissions, ',knowledge,knowledge-qualification')
END
WHERE code = 'bid-administration';
