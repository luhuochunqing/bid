CREATE TABLE IF NOT EXISTS webhook_delivery_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tender_id BIGINT NOT NULL,
    target_url VARCHAR(1000) NOT NULL,
    status_code INT,
    response_body TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_wh_log_tender (tender_id),
    INDEX idx_wh_log_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
