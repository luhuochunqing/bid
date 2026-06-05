-- Input: V1016__performance_blueprint_overhaul.sql
-- Rollback for V1016__performance_blueprint_overhaul.sql

ALTER TABLE performance_record
    DROP INDEX idx_perf_contract_name,
    DROP INDEX idx_perf_customer_type,
    DROP INDEX idx_perf_expiry,
    DROP INDEX idx_perf_signing_entity;

-- Sync contract_name back to project_name for any newly created records before making it NOT NULL
UPDATE performance_record SET project_name = contract_name WHERE project_name IS NULL;

ALTER TABLE performance_record
    MODIFY COLUMN project_name VARCHAR(200) NOT NULL COMMENT '项目名称',
    DROP COLUMN contract_name,
    DROP COLUMN signing_entity,
    DROP COLUMN group_company,
    DROP COLUMN customer_type,
    DROP COLUMN industry,
    DROP COLUMN project_type,
    DROP COLUMN docking_method,
    DROP COLUMN customer_level,
    DROP COLUMN signing_date,
    DROP COLUMN expiry_date,
    DROP COLUMN total_expiry_date,
    DROP COLUMN contact_person,
    DROP COLUMN contact_info,
    DROP COLUMN territory,
    DROP COLUMN customer_address,
    DROP COLUMN xiyu_project_manager,
    DROP COLUMN mall_website_url,
    DROP COLUMN has_bid_notice,
    DROP COLUMN remarks;

ALTER TABLE performance_attachment
    MODIFY COLUMN file_type VARCHAR(50) COMMENT '文件类型: BID_WIN_NOTICE/CONTRACT_SCAN/OTHER';
