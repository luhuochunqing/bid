-- Input: migration-mysql/V108__tender_lifecycle_schema.sql
-- Output: rollback script for postgres environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- §3.6 结项
DROP INDEX IF EXISTS idx_closure_locked;
DROP INDEX IF EXISTS idx_closure_project;
DROP TABLE IF EXISTS project_closure;

-- §3.5 复盘
DROP INDEX IF EXISTS idx_retrospective_project;
DROP TABLE IF EXISTS project_retrospective;

-- §3.4 结果确认
DROP INDEX IF EXISTS idx_result_type;
DROP INDEX IF EXISTS idx_result_project;
DROP TABLE IF EXISTS project_result;

-- §3.3 评标
DROP INDEX IF EXISTS idx_evaluation_project;
DROP TABLE IF EXISTS project_evaluation;

-- §3.1 立项
DROP INDEX IF EXISTS idx_initiation_project;
DROP TABLE IF EXISTS project_initiation_details;

-- §5.4 阶段列
DROP INDEX IF EXISTS idx_projects_stage;
ALTER TABLE projects DROP COLUMN IF EXISTS stage;
