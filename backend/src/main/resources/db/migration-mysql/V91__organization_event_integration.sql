CREATE TABLE IF NOT EXISTS organization_departments (
  department_code VARCHAR(100) PRIMARY KEY,
  department_name VARCHAR(100) NOT NULL,
  parent_department_code VARCHAR(100),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS organization_event_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_key VARCHAR(128) NOT NULL UNIQUE,
  event_topic VARCHAR(100) NOT NULL,
  source_app VARCHAR(100) NOT NULL,
  trace_id VARCHAR(128) NOT NULL,
  payload_hash VARCHAR(64) NOT NULL,
  status ENUM('PROCESSING','PROCESSED','DUPLICATE','REJECTED','FAILED') NOT NULL,
  message VARCHAR(500),
  received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  processed_at TIMESTAMP NULL,
  INDEX idx_organization_event_logs_status(status),
  INDEX idx_organization_event_logs_received_at(received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
