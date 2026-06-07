-- Input: migration-mysql/V109__retrospective_review_columns.sql
-- Output: rollback script for postgres environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

DROP INDEX IF EXISTS idx_retrospective_result_type;
ALTER TABLE project_retrospective DROP COLUMN IF EXISTS review_comment;
ALTER TABLE project_retrospective DROP COLUMN IF EXISTS result_type;
