-- V1003__tender_favorites.sql
-- 标讯收藏功能：用户收藏感兴趣的标讯

CREATE TABLE tender_favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '收藏用户ID',
    tender_id BIGINT NOT NULL COMMENT '标讯ID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',

    UNIQUE KEY uk_user_tender (user_id, tender_id),
    INDEX idx_tender_favorite_user (user_id),
    INDEX idx_tender_favorite_tender (tender_id),
    INDEX idx_tender_favorite_created (user_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标讯收藏';
