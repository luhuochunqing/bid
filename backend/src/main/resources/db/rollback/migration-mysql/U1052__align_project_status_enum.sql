-- Input: migration-mysql/V1052__align_project_status_enum.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- U1052: 回滚 projects.status 枚举至旧 6 值定义
ALTER TABLE projects MODIFY COLUMN status enum (