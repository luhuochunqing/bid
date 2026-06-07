-- Input: migration-mysql/V102__task_content_and_status_fk.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

DROP INDEX idx_tasks_project_status ON tasks;
DROP INDEX idx_tasks_status ON tasks;
-- Manual rollback required for column alteration on tasks.status.
ALTER TABLE tasks DROP COLUMN content;
