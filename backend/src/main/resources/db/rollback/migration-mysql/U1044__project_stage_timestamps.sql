-- U1044__project_stage_timestamps.sql
-- 回滚：删除项目阶段时间戳字段
-- Input: migration-mysql/V1044__project_stage_timestamps.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- 数据回滚说明：删除 initiated_at/evaluating_at/closed_at 列
-- 注意：此操作不可逆，删除前请确认无业务依赖

ALTER TABLE projects
    DROP COLUMN IF EXISTS initiated_at,
    DROP COLUMN IF EXISTS evaluating_at,
    DROP COLUMN IF EXISTS closed_at;
