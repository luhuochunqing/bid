-- Input: migration-mysql/V110__project_lead_assignment.sql
-- Output: rollback script for postgres environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

DROP INDEX IF EXISTS idx_lead_assignment_primary;
DROP INDEX IF EXISTS idx_lead_assignment_project;
DROP TABLE IF EXISTS project_lead_assignment;
