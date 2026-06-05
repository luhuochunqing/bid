-- Input: migration-mysql/V94__wecom_push_binding_and_log.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

DROP INDEX idx_outbound_log_user_created ON notification_outbound_log;
DROP INDEX idx_outbound_log_notification ON notification_outbound_log;
DROP TABLE IF EXISTS notification_outbound_log;
DROP INDEX uk_users_wecom_user_id ON users;
ALTER TABLE users DROP COLUMN wecom_user_id;
