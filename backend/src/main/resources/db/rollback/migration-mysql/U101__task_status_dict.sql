-- Input: migration-mysql/V101__task_status_dict.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- Data rollback required for INSERT INTO task_status_dict; verify seed rows before deleting.
DROP INDEX idx_task_status_dict_enabled_sort ON task_status_dict;
DROP TABLE IF EXISTS task_status_dict;
