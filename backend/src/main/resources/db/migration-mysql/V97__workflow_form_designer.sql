CREATE TABLE IF NOT EXISTS workflow_form_template_drafts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  template_code VARCHAR(80) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  business_type VARCHAR(64) NOT NULL,
  draft_schema_json TEXT NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS workflow_form_template_versions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  template_code VARCHAR(80) NOT NULL,
  name VARCHAR(120) NOT NULL,
  business_type VARCHAR(64) NOT NULL,
  version INT NOT NULL,
  schema_json TEXT NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  published_by VARCHAR(120),
  published_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_workflow_form_template_versions (template_code, version),
  INDEX idx_workflow_form_template_versions_code (template_code, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO workflow_form_template_drafts(template_code, name, business_type, draft_schema_json, enabled, status)
SELECT template_code, name, business_type, schema_json, enabled, 'PUBLISHED'
FROM workflow_form_templates
WHERE NOT EXISTS (
  SELECT 1 FROM workflow_form_template_drafts d WHERE d.template_code = workflow_form_templates.template_code
);

INSERT INTO workflow_form_template_versions(template_code, name, business_type, version, schema_json, enabled, published_by)
SELECT template_code, name, business_type, version, schema_json, enabled, 'migration'
FROM workflow_form_templates
WHERE NOT EXISTS (
  SELECT 1 FROM workflow_form_template_versions v
  WHERE v.template_code = workflow_form_templates.template_code AND v.version = workflow_form_templates.version
);

-- MySQL 8.0 不支持 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`。
-- 用存储过程 + information_schema 前置判断实现幂等，保证 Flyway repair 后重跑不报 Duplicate column。
DROP PROCEDURE IF EXISTS p_add_col_if_missing;
DELIMITER $$
CREATE PROCEDURE p_add_col_if_missing(
  IN p_table VARCHAR(64),
  IN p_column VARCHAR(64),
  IN p_definition VARCHAR(255)
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table
      AND COLUMN_NAME = p_column
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL p_add_col_if_missing('workflow_form_instances', 'template_version', 'INT');
CALL p_add_col_if_missing('workflow_form_instances', 'schema_snapshot_json', 'TEXT');
CALL p_add_col_if_missing('workflow_form_instances', 'oa_binding_snapshot_json', 'TEXT');
CALL p_add_col_if_missing('workflow_form_instances', 'oa_payload_json', 'TEXT');

DROP PROCEDURE IF EXISTS p_add_col_if_missing;
