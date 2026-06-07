-- V146__brand_authorization_library.sql
-- 品牌授权库：供应商品牌授权管理，含到期预警与投标联动
-- Flyway migration MySQL 8.0

CREATE TABLE brand_authorization (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_name VARCHAR(200) NOT NULL COMMENT '授权品牌名称',
    supplier_name VARCHAR(200) NOT NULL COMMENT '授权供应商(原厂/总代)',
    authorization_scope VARCHAR(50) NOT NULL DEFAULT 'NATIONAL' COMMENT '授权范围: NATIONAL/PROVINCIAL/INDUSTRY',
    scope_detail VARCHAR(500) COMMENT '授权范围详情(省域/行业描述)',
    start_date DATE NOT NULL COMMENT '授权开始日期',
    end_date DATE NOT NULL COMMENT '授权截止日期',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/EXPIRING_SOON/EXPIRED/ARCHIVED',
    authorization_doc_url VARCHAR(500) COMMENT '授权书扫描件URL',
    remarks VARCHAR(500) COMMENT '备注',
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    INDEX idx_brand_auth_expiry (end_date),
    INDEX idx_brand_auth_status (status),
    INDEX idx_brand_auth_brand (brand_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='品牌授权';

CREATE TABLE brand_auth_alert_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用提醒',
    alert_days_before INT NOT NULL DEFAULT 30 COMMENT '到期前多少天提醒',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='品牌授权到期提醒配置';
