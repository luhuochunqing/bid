-- 投标关键节点提醒默认值调整：24小时 -> 72小时（3天）
-- 配合"每日重复提醒"改造：从提醒触发开始，每天发送一次，直到截止
-- 仅修改 DEFAULT 值，不 UPDATE 存量数据（存量设置保持用户已配置值）
-- last_notified_at 字段语义从"是否发送过标记"变为"24小时去重基准"

ALTER TABLE tender_reminder_settings
    MODIFY COLUMN remind_before_hours INT DEFAULT 72 COMMENT '提前提醒小时数（默认72小时=3天，进入提醒窗口后每日重复）';
