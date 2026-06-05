-- V100: 组织架构目录同步——新增字段及同步运行记录表
-- 幂等：ADD COLUMN / CREATE INDEX 全部改为存储过程幂等判断

DELIMITER $$

DROP PROCEDURE IF EXISTS add_column_if_not_exists$$
CREATE PROCEDURE add_column_if_not_exists(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_column_def VARCHAR(1000)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND COLUMN_NAME = p_column_name
    ) THEN
        SET @sql = CONCAT('ALTER TABLE ', p_table_name, ' ADD COLUMN ', p_column_name, ' ', p_column_def);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS add_index_if_not_exists$$
CREATE PROCEDURE add_index_if_not_exists(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_index_def VARCHAR(1000)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND INDEX_NAME = p_index_name
    ) THEN
        SET @sql = CONCAT('CREATE INDEX ', p_index_name, ' ', p_index_def);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

-- organization_event_logs 新增字段
CALL add_column_if_not_exists('organization_event_logs', 'upstream_event_key', 'VARCHAR(128)');
CALL add_column_if_not_exists('organization_event_logs', 'span_id',         'VARCHAR(128)');
CALL add_column_if_not_exists('organization_event_logs', 'parent_id',        'VARCHAR(128)');
CALL add_column_if_not_exists('organization_event_logs', 'event_time',       'TIMESTAMP NULL');
CALL add_column_if_not_exists('organization_event_logs', 'entity_type',      'VARCHAR(32)');
CALL add_column_if_not_exists('organization_event_logs', 'external_user_id', 'VARCHAR(128)');
CALL add_column_if_not_exists('organization_event_logs', 'external_dept_id', 'VARCHAR(128)');
CALL add_column_if_not_exists('organization_event_logs', 'raw_payload',     'TEXT');
CALL add_column_if_not_exists('organization_event_logs', 'retry_count',    'INT NOT NULL DEFAULT 0');
CALL add_column_if_not_exists('organization_event_logs', 'next_retry_at',   'TIMESTAMP NULL');
CALL add_column_if_not_exists('organization_event_logs', 'last_error_code', 'VARCHAR(100)');

CALL add_index_if_not_exists('organization_event_logs', 'idx_org_event_logs_upstream_key',    'ON organization_event_logs(upstream_event_key)');
CALL add_index_if_not_exists('organization_event_logs', 'idx_org_event_logs_external_user',  'ON organization_event_logs(source_app, external_user_id)');
CALL add_index_if_not_exists('organization_event_logs', 'idx_org_event_logs_external_dept',  'ON organization_event_logs(source_app, external_dept_id)');
CALL add_index_if_not_exists('organization_event_logs', 'idx_org_event_logs_next_retry',     'ON organization_event_logs(status, next_retry_at)');

-- organization_departments 新增字段
CALL add_column_if_not_exists('organization_departments', 'external_dept_id',         'VARCHAR(128)');
CALL add_column_if_not_exists('organization_departments', 'parent_external_dept_id',   'VARCHAR(128)');
CALL add_column_if_not_exists('organization_departments', 'source_app',                'VARCHAR(100)');
CALL add_column_if_not_exists('organization_departments', 'last_event_key',            'VARCHAR(128)');
CALL add_column_if_not_exists('organization_departments', 'last_synced_at',            'TIMESTAMP NULL');

CALL add_index_if_not_exists('organization_departments', 'idx_org_departments_external',      'ON organization_departments(source_app, external_dept_id)');
CALL add_index_if_not_exists('organization_departments', 'idx_org_departments_parent_external', 'ON organization_departments(source_app, parent_external_dept_id)');

-- users 新增字段
CALL add_column_if_not_exists('users', 'external_org_user_id',    'VARCHAR(128)');
CALL add_column_if_not_exists('users', 'external_org_source_app', 'VARCHAR(100)');
CALL add_column_if_not_exists('users', 'last_org_event_key',     'VARCHAR(128)');
CALL add_column_if_not_exists('users', 'last_org_synced_at',     'TIMESTAMP NULL');

CALL add_index_if_not_exists('users', 'idx_users_external_org_user', 'ON users(external_org_source_app, external_org_user_id)');

-- 同步运行记录表
CREATE TABLE IF NOT EXISTS organization_sync_runs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  run_key VARCHAR(128) NOT NULL UNIQUE,
  run_type VARCHAR(32) NOT NULL,
  source_app VARCHAR(100) NOT NULL,
  status VARCHAR(32) NOT NULL,
  triggered_by VARCHAR(100),
  started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at TIMESTAMP NULL,
  total_count INT NOT NULL DEFAULT 0,
  success_count INT NOT NULL DEFAULT 0,
  failed_count INT NOT NULL DEFAULT 0,
  last_error_code VARCHAR(100),
  last_error_message VARCHAR(500),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_org_sync_runs_source_status(source_app, status),
  INDEX idx_org_sync_runs_started_at(started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS organization_sync_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  run_id BIGINT NOT NULL,
  entity_type VARCHAR(32) NOT NULL,
  external_user_id VARCHAR(128),
  external_dept_id VARCHAR(128),
  internal_user_id BIGINT,
  department_code VARCHAR(100),
  action_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  event_key VARCHAR(128),
  error_code VARCHAR(100),
  error_message VARCHAR(500),
  raw_payload TEXT,
  processed_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_org_sync_items_run FOREIGN KEY (run_id) REFERENCES organization_sync_runs(id),
  INDEX idx_org_sync_items_run(run_id),
  INDEX idx_org_sync_items_run_status(run_id, status),
  INDEX idx_org_sync_items_external_user(entity_type, external_user_id),
  INDEX idx_org_sync_items_external_dept(entity_type, external_dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;
