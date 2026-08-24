-- V1200: 商机预测提醒日志表
--
-- 背景：商机预测提醒"每项目只提醒一次"，需要独立日志表记录已提醒的项目，
-- 避免依赖 notification 表（其记录可能被清理/归档）导致重复提醒。
--
-- 唯一键 project_id：同一项目至多提醒一次，提供幂等保证；
-- 插入前查该项目是否有记录即可判定是否已提醒。

CREATE TABLE IF NOT EXISTS opportunity_prediction_reminder_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    predicted_bid_date DATE NOT NULL,
    purchaser_name VARCHAR(500),
    notified_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_opportunity_prediction_reminder_project (project_id),
    INDEX idx_opportunity_prediction_reminder_predicted (predicted_bid_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
