-- Input: migration-mysql/V1200__add_opportunity_prediction_reminder_log.sql
-- Output: rollback script for mysql environments; drops the reminder log table (audit data loss).
-- Pos: Flyway down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

DROP TABLE IF EXISTS opportunity_prediction_reminder_log;
