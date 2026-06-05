-- Input: migration-mysql/V112__initiation_details_align.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

ALTER TABLE project_initiation_details
    DROP COLUMN owner_unit,
    DROP COLUMN project_type,
    DROP COLUMN owner_user_id;
