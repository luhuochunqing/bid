-- 自动提醒不写入 tender_reminder_settings，发送日志允许没有设置 ID。
ALTER TABLE tender_reminder_logs
    MODIFY COLUMN reminder_setting_id BIGINT NULL COMMENT '提醒设置ID，自动提醒为空';
