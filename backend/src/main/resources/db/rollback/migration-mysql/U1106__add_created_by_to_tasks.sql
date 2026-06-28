-- Input: V1106__add_created_by_to_tasks.sql
-- U1106: Rollback for V1106__add_created_by_to_tasks.sql
-- 删除 tasks.created_by 列(回滚到 CO-382 之前的状态)
-- 注意:回滚会丢失已记录的创建人信息,仅在必要时执行。
-- 注意:MySQL 8.0 不支持 DROP COLUMN IF EXISTS(MariaDB 语法),使用标准 ALTER TABLE。

ALTER TABLE tasks
    DROP COLUMN created_by;
