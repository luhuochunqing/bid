-- 物理删除已同步但不属于白名单的 OSS 测试用户
-- 使用步骤：
-- 1. 先执行 SELECT 确认要删除的范围；
-- 2. 将白名单邮箱/工号/用户名填入 IN (...) 列表；
-- 3. 确认这些用户没有业务数据（项目、任务、标讯等外键引用），否则 DELETE 会失败；
-- 4. 再执行 DELETE。

-- 示例：查询所有外部来源为 oss 且不在白名单里的用户
-- SELECT id, username, full_name, email, external_org_user_id, department_name
-- FROM users
-- WHERE external_org_source_app = 'oss'
--   AND username NOT IN ('zhangdi', 'zhengrongrong', 'yuansiqi', ...);

-- 示例：物理删除（请先替换白名单值）
-- DELETE FROM users
-- WHERE external_org_source_app = 'oss'
--   AND username NOT IN ('zhangdi', 'zhengrongrong', 'yuansiqi', ...);

-- 如果存在外键引用导致无法删除，可改用禁用（物理数据保留，禁止登录）：
-- UPDATE users
-- SET enabled = false
-- WHERE external_org_source_app = 'oss'
--   AND username NOT IN ('zhangdi', 'zhengrongrong', 'yuansiqi', ...);
