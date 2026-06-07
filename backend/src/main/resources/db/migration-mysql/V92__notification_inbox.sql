-- Notification content (one row per notification)
CREATE TABLE IF NOT EXISTS notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    source_entity_type VARCHAR(50),
    source_entity_id BIGINT,
    title VARCHAR(200) NOT NULL,
    body TEXT,
    payload_json TEXT,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notification_source(source_entity_type, source_entity_id),
    INDEX idx_notification_created(created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Per-user notification state
CREATE TABLE IF NOT EXISTS user_notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    notification_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    read_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_notification_notification_user(notification_id, user_id),
    INDEX idx_user_notification_user_read(user_id, read_at),
    INDEX idx_user_notification_user_created(user_id, created_at),
    CONSTRAINT fk_user_notification_notification
        FOREIGN KEY (notification_id) REFERENCES notification(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
