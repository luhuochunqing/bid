-- V115__knowledge_base_tables.sql
-- 知识库模块基础表结构设计

CREATE TABLE project_archive (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT UNIQUE NOT NULL,
    project_name VARCHAR(200) NOT NULL,
    archive_status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE archive_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    archive_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    document_category VARCHAR(50) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    upload_user_id BIGINT NOT NULL,
    upload_user_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_archive_file_archive_id FOREIGN KEY (archive_id) REFERENCES project_archive(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE archive_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    archive_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(100) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    action_content VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE knowledge_case (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_project_id BIGINT NOT NULL,
    source_project_name VARCHAR(200) NOT NULL,
    scoring_point_title VARCHAR(200) NOT NULL,
    requirement_raw TEXT NOT NULL,
    response_text TEXT NOT NULL,
    reuse_count INT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL,
    customer_type VARCHAR(50),
    project_type VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE deposit_tracking (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    payment_date DATE NOT NULL,
    expected_return_date DATE NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    payee VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
