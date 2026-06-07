-- V1054: Add webhook delivery task and DLQ tables
-- §4.2 #1/1 异步治理收口 — 持久化 webhook 投递任务与死信队列
-- PR: N/A (local agent change)
-- 为 webhook 持久化投递任务与死信队列表建立 MySQL 8.0 表结构。

CREATE TABLE IF NOT EXISTS webhook_delivery_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tender_id BIGINT NOT NULL,
    external_id VARCHAR(128) NULL,
    target_url VARCHAR(1000) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    business_key VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME NULL,
    last_error_code VARCHAR(128) NULL,
    last_error_message VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_wh_task_status_retry (status, next_retry_at),
    INDEX idx_wh_task_tender (tender_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS webhook_delivery_dlq (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    tender_id BIGINT NOT NULL,
    business_key VARCHAR(255) NOT NULL,
    reason_code VARCHAR(128) NOT NULL,
    error_message VARCHAR(1000) NULL,
    payload TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_wh_dlq_task (task_id),
    INDEX idx_wh_dlq_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
