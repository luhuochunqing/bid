-- V1009: Refactor brand authorization to blueprint-aligned manufacturer_authorization
-- Rename old table to deprecated; create new tables per §4.6a data model

ALTER TABLE brand_authorization RENAME TO brand_authorization_deprecated;

CREATE TABLE manufacturer_authorization (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_line VARCHAR(50) NOT NULL COMMENT '一级产线 (39-item enum)',
    brand_id VARCHAR(100) NOT NULL COMMENT '品牌ID',
    brand_name VARCHAR(200) NOT NULL COMMENT '品牌',
    import_domestic VARCHAR(10) NOT NULL COMMENT '进口/国产',
    manufacturer_name VARCHAR(200) NOT NULL COMMENT '品牌原厂名称 (法人全称)',
    auth_start_date DATE NOT NULL COMMENT '授权开始时间',
    auth_end_date DATE NOT NULL COMMENT '授权结束时间',
    remarks VARCHAR(1000) COMMENT '备注',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'DRAFT/ACTIVE/EXPIRING_SOON/EXPIRED/REVOKED',
    revoke_reason VARCHAR(500) COMMENT '作废原因',
    created_by BIGINT COMMENT '创建人用户ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    INDEX idx_ma_brand_id (brand_id),
    INDEX idx_ma_product_line (product_line),
    INDEX idx_ma_status (status),
    INDEX idx_ma_end_date (auth_end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='原厂授权';

CREATE TABLE brand_auth_attachment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    authorization_id BIGINT NOT NULL COMMENT 'FK → manufacturer_authorization.id',
    attachment_type VARCHAR(20) NOT NULL COMMENT 'AUTH_DOC or SUPPLEMENTARY',
    file_name VARCHAR(255) NOT NULL,
    file_url VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL COMMENT 'bytes',
    file_type VARCHAR(100) COMMENT 'MIME type',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_baa_auth_id (authorization_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品牌授权附件';
