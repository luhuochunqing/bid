-- Input: migration-mysql/V1168__tender_reminder_default_72h.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway rollback coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- 回滚 V1168：投标关键节点提醒默认值恢复为 24 小时
-- 注意：回滚仅恢复 DEFAULT 值，不会重置已发送提醒的 last_notified_at
-- 如需彻底回到"只发一次"语义，需要业务侧人工清理 last_notified_at 字段

ALTER TABLE tender_reminder_settings
    MODIFY COLUMN remind_before_hours INT DEFAULT 24 COMMENT '提前提醒小时数';
