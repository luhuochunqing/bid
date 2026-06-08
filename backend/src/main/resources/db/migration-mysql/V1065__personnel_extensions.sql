-- Add birth_date to personnel
ALTER TABLE personnel ADD COLUMN birth_date DATE;

-- Add certificate extensions
ALTER TABLE personnel_certificate ADD COLUMN title VARCHAR(50);
ALTER TABLE personnel_certificate ADD COLUMN is_permanent BOOLEAN DEFAULT FALSE;

-- Add education extension
ALTER TABLE personnel_education ADD COLUMN is_highest_education_school BOOLEAN DEFAULT FALSE;

-- Create operation log table
CREATE TABLE personnel_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    personnel_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(100),
    operation_type VARCHAR(50) NOT NULL,
    change_details JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_personnel_id (personnel_id),
    INDEX idx_created_at (created_at)
);
