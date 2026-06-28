-- V1106: Add created_by column to tasks table for CO-382
-- Records the username of the task creator so the kanban can display "创建人"
-- instead of the stale "系统自动获取" placeholder.
--
-- 修复历史:CO-382(commit 0834deb91)原迁移误放至 db/migration/(历史遗留目录,
-- Flyway 不读取),导致生产 DB 缺少 created_by 列,引发 /api/tasks/my 500 错误。
-- 本次迁移放至正确目录 db/migration-mysql/,版本号 V1106(V121 已被占用)。
-- 注意:MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS(MariaDB 语法),使用标准 ALTER TABLE。

ALTER TABLE tasks
    ADD COLUMN created_by VARCHAR(255) DEFAULT NULL AFTER completion_notes;
