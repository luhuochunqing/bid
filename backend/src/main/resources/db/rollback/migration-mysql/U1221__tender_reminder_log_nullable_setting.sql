-- Input: migration-mysql/V1221__tender_reminder_log_nullable_setting.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- 回滚前把自动提醒日志归到 0，避免 NOT NULL 失败。0 不是真实设置行。
UPDATE tender_reminder_logs SET reminder_setting_id = 0 WHERE reminder_setting_id IS NULL;
ALTER TABLE tender_reminder_logs
    MODIFY COLUMN reminder_setting_id BIGINT NOT NULL COMMENT '提醒设置ID';
