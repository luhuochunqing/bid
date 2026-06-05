-- Input: migration-mysql/V1035__retrospective_blueprint_fields.sql
-- Output: rollback script for mysql environments; 回退 V1035 新增列。
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.
ALTER TABLE project_retrospective
    DROP COLUMN meeting_time,
    DROP COLUMN meeting_format,
    DROP COLUMN meeting_participants,
    DROP COLUMN loss_reason_flags,
    DROP COLUMN post_win_improvements,
    DROP COLUMN process_problems,
    DROP COLUMN post_loss_measures,
    DROP COLUMN report_file_ids;
