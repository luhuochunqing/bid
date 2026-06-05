-- Inbox P1: subscription + mention tables
--
-- 新增：
--   1. subscription  — 多态关注（user 关注 任意实体）
--   2. mention       — @ 提及记录（审计 + 源实体关联）
--
-- 依赖 V92 的 notification 表。

CREATE TABLE IF NOT EXISTS subscription (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    target_entity_type VARCHAR(50) NOT NULL,
    target_entity_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_subscription_user_target(user_id, target_entity_type, target_entity_id),
    INDEX idx_subscription_target(target_entity_type, target_entity_id),
    INDEX idx_subscription_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS mention (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    notification_id BIGINT NOT NULL,
    mentioner_user_id BIGINT NOT NULL,
    mentioned_user_id BIGINT NOT NULL,
    source_entity_type VARCHAR(50),
    source_entity_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_mention_mentioned(mentioned_user_id),
    INDEX idx_mention_source(source_entity_type, source_entity_id),
    CONSTRAINT fk_mention_notification
        FOREIGN KEY (notification_id) REFERENCES notification(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
