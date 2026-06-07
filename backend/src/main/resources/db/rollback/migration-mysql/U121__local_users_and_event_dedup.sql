-- Input: migration-mysql/V121__local_users_and_event_dedup.sql
-- Output: rollback script for mysql environments; removes local_users table and dedup index.
-- Pos: Flyway down migration coverage for 西域数智化投标管理平台.

DROP INDEX uk_event_dedup ON organization_event_logs;

DROP TABLE IF EXISTS local_users;
