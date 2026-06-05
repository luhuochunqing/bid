-- Input: migration-mysql/V123__tender_reminder_settings.sql
-- Output: rollback script for mysql environments; removes tender_reminder_logs and tender_reminder_settings tables.
-- Pos: Flyway down migration coverage for 西域数智化投标管理平台.

DROP TABLE IF EXISTS tender_reminder_logs;

DROP TABLE IF EXISTS tender_reminder_settings;
