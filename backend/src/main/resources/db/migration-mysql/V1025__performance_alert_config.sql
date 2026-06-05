-- V1025__performance_alert_config.sql
-- 蓝图表格 §4.5.1.9 业绩合同到期提醒规则配置
-- 差异化提醒窗口：央企 180 天 / 其他客户 90 天
-- Flyway migration MySQL 8.0

CREATE TABLE performance_alert_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_days_soe INT NOT NULL DEFAULT 180 COMMENT '央企（CENTRAL_SOE）提前提醒天数',
    alert_days_default INT NOT NULL DEFAULT 90 COMMENT '其他客户类型提前提醒天数',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '提醒功能启用开关',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业绩合同到期提醒全局配置';

-- 插入默认配置（央企 180 天，其他 90 天，启用）
INSERT INTO performance_alert_config (alert_days_soe, alert_days_default, enabled)
VALUES (180, 90, 1);
