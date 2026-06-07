-- Input: migration-mysql/V103__task_extended_fields.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

ALTER TABLE tasks DROP COLUMN extended_fields_json;
DROP INDEX idx_task_extended_field_enabled_sort ON task_extended_field;
DROP TABLE IF EXISTS task_extended_field;
