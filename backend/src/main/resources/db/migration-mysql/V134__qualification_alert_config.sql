CREATE TABLE qualification_alert_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_days INT NOT NULL DEFAULT 90 COMMENT '提前提醒天数',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资质证书到期提醒全局告警规则配置';

-- 插入默认配置（提前 90 天提醒，启用）
INSERT INTO qualification_alert_config (alert_days, enabled) VALUES (90, 1);
