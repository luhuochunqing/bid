-- V1055: Add notification delivery task and DLQ tables
-- §4.2 #1/1 异步治理收口 — 持久化通知投递任务与死信队列
CREATE TABLE notification_delivery_task (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    notification_id BIGINT NOT NULL,
    recipient_user_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    business_key VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP NULL,
    last_error_code VARCHAR(100) NULL,
    last_error_message VARCHAR(1000) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_notification_delivery_task_business_key UNIQUE (business_key),
    CONSTRAINT fk_notification_delivery_task_notification FOREIGN KEY (notification_id) REFERENCES notification(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_notification_delivery_task_status_retry ON notification_delivery_task(status, next_retry_at);
CREATE INDEX idx_notification_delivery_task_notification_recipient ON notification_delivery_task(notification_id, recipient_user_id);

CREATE TABLE notification_delivery_dlq (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    notification_id BIGINT NOT NULL,
    recipient_user_id BIGINT NOT NULL,
    business_key VARCHAR(255) NOT NULL,
    reason_code VARCHAR(100) NOT NULL,
    error_message VARCHAR(1000) NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_delivery_dlq_task FOREIGN KEY (task_id) REFERENCES notification_delivery_task(id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_delivery_dlq_notification FOREIGN KEY (notification_id) REFERENCES notification(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_notification_delivery_dlq_notification ON notification_delivery_dlq(notification_id);
