-- V144__personnel_library.sql
-- 人员库：投标团队成员资历管理，含执业证书到期提醒
-- Flyway migration MySQL 8.0

CREATE TABLE personnel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '姓名',
    employee_number VARCHAR(50) NOT NULL UNIQUE COMMENT '工号',
    department_code VARCHAR(50) COMMENT '部门编码',
    department_name VARCHAR(100) COMMENT '部门名称',
    education VARCHAR(50) COMMENT '学历',
    technical_title VARCHAR(100) COMMENT '技术职称',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/INACTIVE/TERMINATED',
    attachment_url VARCHAR(500) COMMENT '附件URL(身份证/毕业证等)',
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    INDEX idx_personnel_status (status),
    INDEX idx_personnel_department (department_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人员库';

CREATE TABLE personnel_certificate (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    personnel_id BIGINT NOT NULL COMMENT '人员ID',
    certificate_name VARCHAR(200) NOT NULL COMMENT '证书名称',
    certificate_number VARCHAR(100) COMMENT '证书编号',
    certificate_type VARCHAR(50) NOT NULL COMMENT '证书类型',
    issue_date DATE COMMENT '发证日期',
    expiry_date DATE COMMENT '到期日期',
    attachment_url VARCHAR(500) COMMENT '证书扫描件URL',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (personnel_id) REFERENCES personnel(id) ON DELETE CASCADE,
    INDEX idx_cert_expiry (expiry_date),
    INDEX idx_cert_type (certificate_type),
    INDEX idx_cert_personnel (personnel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人员执业证书';

CREATE TABLE personnel_alert_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用提醒',
    alert_days_before INT NOT NULL DEFAULT 60 COMMENT '到期前多少天提醒',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人员证书到期提醒配置';
