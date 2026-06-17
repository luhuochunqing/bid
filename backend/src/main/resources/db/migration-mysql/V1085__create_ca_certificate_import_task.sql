-- V1085: CA证书批量导入任务表
CREATE TABLE IF NOT EXISTS ca_certificate_import_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_rows INT DEFAULT 0,
    valid_rows INT DEFAULT 0,
    invalid_rows INT DEFAULT 0,
    imported_rows INT DEFAULT 0,
    updated_rows INT DEFAULT 0,
    error_details TEXT,
    source_filename VARCHAR(500),
    created_by BIGINT,
    created_by_username VARCHAR(100),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
