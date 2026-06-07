CREATE TABLE IF NOT EXISTS workflow_form_templates (
  template_code VARCHAR(80) PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  business_type VARCHAR(64) NOT NULL,
  version INT NOT NULL,
  schema_json TEXT NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS workflow_form_instances (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  business_type VARCHAR(64) NOT NULL,
  template_code VARCHAR(80) NOT NULL,
  project_id BIGINT,
  applicant_name VARCHAR(120),
  status VARCHAR(32) NOT NULL,
  form_data_json TEXT NOT NULL,
  oa_instance_id VARCHAR(120),
  business_applied BOOLEAN NOT NULL DEFAULT FALSE,
  business_apply_error VARCHAR(500),
  oa_operator_name VARCHAR(120),
  oa_comment VARCHAR(500),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_workflow_form_instances_project_status(project_id, status),
  INDEX idx_workflow_form_instances_type_status(business_type, status),
  INDEX idx_workflow_form_instances_oa(oa_instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS oa_process_bindings (
  template_code VARCHAR(80) PRIMARY KEY,
  provider VARCHAR(32) NOT NULL,
  workflow_code VARCHAR(120) NOT NULL,
  field_mapping_json TEXT,
  enabled BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS oa_process_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  form_instance_id BIGINT,
  oa_instance_id VARCHAR(120),
  event_id VARCHAR(120) NOT NULL UNIQUE,
  event_type VARCHAR(64) NOT NULL,
  raw_payload TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO workflow_form_templates(template_code, name, business_type, version, schema_json, enabled)
VALUES (
  'QUALIFICATION_BORROW',
  '资质借阅申请',
  'QUALIFICATION_BORROW',
  1,
  '{"fields":[{"key":"qualificationId","label":"资质","type":"qualification","required":true},{"key":"borrower","label":"借用人","type":"text","required":true},{"key":"department","label":"部门","type":"text","required":true},{"key":"projectId","label":"项目","type":"project","required":true},{"key":"purpose","label":"用途","type":"textarea","required":true},{"key":"expectedReturnDate","label":"预计归还日期","type":"date","required":true},{"key":"remark","label":"备注","type":"textarea","required":false}]}',
  TRUE
) ON DUPLICATE KEY UPDATE name = VALUES(name), schema_json = VALUES(schema_json), enabled = VALUES(enabled);

INSERT INTO oa_process_bindings(template_code, provider, workflow_code, field_mapping_json, enabled)
VALUES ('QUALIFICATION_BORROW', 'WEAVER', 'WF_QUALIFICATION_BORROW', '{}', TRUE)
ON DUPLICATE KEY UPDATE provider = VALUES(provider), workflow_code = VALUES(workflow_code), enabled = VALUES(enabled);
