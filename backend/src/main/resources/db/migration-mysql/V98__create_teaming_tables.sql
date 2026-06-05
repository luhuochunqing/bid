CREATE TABLE IF NOT EXISTS sys_crm_customer_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id VARCHAR(100) NOT NULL,
    user_id BIGINT NOT NULL,
    permission_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_crm_perm_customer (customer_id),
    INDEX idx_crm_perm_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_project_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    member_role VARCHAR(50),
    permission_level VARCHAR(50) NOT NULL,
    is_inherited BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX uk_project_user (project_id, user_id),
    INDEX idx_project_member_project (project_id),
    INDEX idx_project_member_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
