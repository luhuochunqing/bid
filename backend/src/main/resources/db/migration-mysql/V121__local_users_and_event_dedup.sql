CREATE TABLE IF NOT EXISTS local_users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(64) NOT NULL UNIQUE,
  user_name VARCHAR(128) NOT NULL,
  email VARCHAR(256),
  mobile VARCHAR(32),
  dept_id VARCHAR(64),
  position VARCHAR(128),
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  source_updated_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_local_users_status (status),
  INDEX idx_local_users_dept_id (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE organization_event_logs
  ADD UNIQUE INDEX uk_event_dedup (trace_id, span_id, event_topic);
