-- Input: migration-mysql/V1222__create_case_precipitation_task.sql
-- Output: rollback script for mysql environments; dropping the table loses task history.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

DROP TABLE IF EXISTS case_precipitation_task;
