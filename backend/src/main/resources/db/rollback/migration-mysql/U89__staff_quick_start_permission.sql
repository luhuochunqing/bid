-- Input: migration-mysql/V89__staff_quick_start_permission.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- Data rollback required for UPDATE roles; original values are not stored in migration history.
