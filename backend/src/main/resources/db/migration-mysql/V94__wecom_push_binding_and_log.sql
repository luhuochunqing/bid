-- V94 WeCom push notification adaptation (MySQL variant)
--
-- MySQL/InnoDB and H2 allow multiple NULL values in a UNIQUE
-- index here, while enforcing uniqueness for non-null wecom_user_id values.

ALTER TABLE users ADD COLUMN wecom_user_id VARCHAR(64);
CREATE UNIQUE INDEX uk_users_wecom_user_id ON users(wecom_user_id);

CREATE TABLE notification_outbound_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    notification_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    channel VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    skip_reason VARCHAR(50),
    wecom_errcode INT,
    wecom_errmsg VARCHAR(500),
    attempt_count INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_outbound_log_notification FOREIGN KEY (notification_id) REFERENCES notification(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_outbound_log_notification ON notification_outbound_log(notification_id);
CREATE INDEX idx_outbound_log_user_created ON notification_outbound_log(user_id, created_at DESC);
