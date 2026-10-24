-- Input: migration-mysql/V1223__fix_case_precipitation_task_id_type.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

ALTER TABLE case_precipitation_task
    MODIFY COLUMN task_id CHAR(36) NOT NULL COMMENT 'AI案例沉淀任务 ID（UUID）';
