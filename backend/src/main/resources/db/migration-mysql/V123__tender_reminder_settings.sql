-- 标讯提醒设置表
-- 支持报名截止提醒和开标提醒

CREATE TABLE tender_reminder_settings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tender_id BIGINT NOT NULL COMMENT '标讯ID',
    reminder_type VARCHAR(50) NOT NULL COMMENT '提醒类型: REGISTRATION_DEADLINE=报名截止, BID_OPENING=开标',
    remind_before_hours INT DEFAULT 24 COMMENT '提前提醒小时数',
    reminder_targets JSON COMMENT '通知对象列表 [{"userId": 1, "userName": "张三", "wecomUserId": "xxx"}]',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    last_notified_at TIMESTAMP NULL COMMENT '最后通知时间',
    created_by BIGINT COMMENT '创建人ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tender_reminder_tender (tender_id),
    INDEX idx_tender_reminder_type (reminder_type),
    INDEX idx_tender_reminder_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 已发送提醒记录表
CREATE TABLE tender_reminder_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reminder_setting_id BIGINT NOT NULL COMMENT '提醒设置ID',
    tender_id BIGINT NOT NULL COMMENT '标讯ID',
    reminder_type VARCHAR(50) NOT NULL COMMENT '提醒类型',
    recipient_user_id BIGINT NOT NULL COMMENT '通知对象ID',
    recipient_wecom_user_id VARCHAR(100) COMMENT '企微用户ID',
    status VARCHAR(20) NOT NULL COMMENT '发送状态: SENT=已发送, FAILED=失败, SKIPPED=跳过',
    error_message VARCHAR(500) COMMENT '错误信息',
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    INDEX idx_reminder_log_setting (reminder_setting_id),
    INDEX idx_reminder_log_tender (tender_id),
    INDEX idx_reminder_log_sent_at (sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
