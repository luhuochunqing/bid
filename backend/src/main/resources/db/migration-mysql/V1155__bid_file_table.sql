-- 大文件上传元数据表（华为云 OBS 直传）
-- 文件数据流直接由浏览器写入 OBS，本表只记录上传状态与元数据
CREATE TABLE IF NOT EXISTS bid_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    upload_id VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    original_name VARCHAR(500) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    bucket VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    file_hash VARCHAR(64),
    mime_type VARCHAR(100),
    creator_id BIGINT NOT NULL,
    error_message VARCHAR(2000),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6),
    CONSTRAINT uk_bid_file_upload_id UNIQUE (upload_id),
    CONSTRAINT fk_bid_file_creator FOREIGN KEY (creator_id) REFERENCES users (id)
);

CREATE INDEX idx_bid_file_status ON bid_file(status);
CREATE INDEX idx_bid_file_creator ON bid_file(creator_id);
CREATE INDEX idx_bid_file_created_at ON bid_file(created_at);
