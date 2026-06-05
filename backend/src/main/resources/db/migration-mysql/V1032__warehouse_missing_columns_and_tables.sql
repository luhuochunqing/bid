-- V1032: warehouse 缺失列 + 子表补建
-- warehouse 表缺少 close_reason 和 version 列（JPA @Version 乐观锁）
-- warehouse_attachment/import_task/export_task/operation_log 四张子表
-- 此前仅由 Hibernate ddl-auto 创建，Flyway 链中缺失

-- 1. warehouse 表补列（幂等：基于 information_schema 判断）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'warehouse' AND COLUMN_NAME = 'close_reason');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE warehouse ADD COLUMN close_reason VARCHAR(500) NULL COMMENT "关仓原因"', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists2 = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'warehouse' AND COLUMN_NAME = 'version');
SET @sql2 = IF(@col_exists2 = 0, 'ALTER TABLE warehouse ADD COLUMN version BIGINT NULL COMMENT "乐观锁版本号"', 'SELECT 1');
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- 2. 子表补建（幂等：IF NOT EXISTS）
CREATE TABLE IF NOT EXISTS warehouse_attachment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    warehouse_id BIGINT NOT NULL,
    type ENUM('PROPERTY_CERTIFICATE','INVOICE','PHOTOS') NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    uploaded_by BIGINT NOT NULL,
    uploaded_at DATETIME NOT NULL,
    UNIQUE KEY uk_attachment_warehouse_type_stored (warehouse_id, type, stored_filename),
    CONSTRAINT fk_attachment_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库附件';

CREATE TABLE IF NOT EXISTS warehouse_import_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    status ENUM('PENDING','VALIDATING','VALIDATED','IMPORTING','COMPLETED','FAILED') NOT NULL,
    total_rows INT NULL,
    valid_rows INT NULL,
    invalid_rows INT NULL,
    imported_rows INT NULL,
    error_details TEXT NULL,
    source_file_path VARCHAR(500) NULL,
    source_filename VARCHAR(255) NULL,
    created_by BIGINT NOT NULL,
    created_by_username VARCHAR(100) NULL,
    created_at DATETIME NOT NULL,
    completed_at DATETIME NULL,
    failure_reason VARCHAR(500) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库导入任务';

CREATE TABLE IF NOT EXISTS warehouse_export_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    status ENUM('PENDING','PROCESSING','COMPLETED','FAILED') NOT NULL,
    filter_snapshot TEXT NULL,
    total_count INT NULL,
    stored_file_path VARCHAR(500) NULL,
    download_url VARCHAR(500) NULL,
    expires_at DATETIME NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    completed_at DATETIME NULL,
    failure_reason VARCHAR(500) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库导出任务';

CREATE TABLE IF NOT EXISTS warehouse_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    warehouse_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    operator_id BIGINT NULL,
    operator_username VARCHAR(100) NULL,
    action_type ENUM('CREATE','EDIT','CLOSE','RESTORE','ATTACH_UPLOAD','ATTACH_DELETE') NOT NULL,
    field_name VARCHAR(100) NULL,
    old_value VARCHAR(500) NULL,
    new_value VARCHAR(500) NULL,
    description TEXT NULL,
    CONSTRAINT fk_oplog_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库操作日志';
