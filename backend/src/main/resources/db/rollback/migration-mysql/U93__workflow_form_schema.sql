-- Input: migration-mysql/V93__workflow_form_schema.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- Data rollback required for INSERT INTO oa_process_bindings; verify seed rows before deleting.
-- Data rollback required for INSERT INTO workflow_form_templates; verify seed rows before deleting.
DROP TABLE IF EXISTS oa_process_events;
DROP TABLE IF EXISTS oa_process_bindings;
DROP TABLE IF EXISTS workflow_form_instances;
DROP TABLE IF EXISTS workflow_form_templates;
