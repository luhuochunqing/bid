CREATE TABLE IF NOT EXISTS api_keys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    scopes VARCHAR(1000) NOT NULL,
    status ENUM('active','disabled','expired') NOT NULL DEFAULT 'active',
    created_by VARCHAR(100),
    expires_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_api_key_hash (key_hash),
    INDEX idx_api_key_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
