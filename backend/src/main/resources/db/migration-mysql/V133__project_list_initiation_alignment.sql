-- V133: Align project_initiation_details with PRD §4.3 project list & initiation fields
-- Adds nullable columns for list display and new initiation form fields
-- customer_info_json already exists (V125); this migration adds missing list-projection fields

ALTER TABLE project_initiation_details
    ADD COLUMN customer_grade VARCHAR(32) NULL COMMENT '客户等级',
    ADD COLUMN bid_status VARCHAR(32) NULL COMMENT '投标状态',
    ADD COLUMN bidding_leader_name VARCHAR(100) NULL COMMENT '投标负责人姓名',
    ADD COLUMN bidding_platform VARCHAR(255) NULL COMMENT '投标平台',
    ADD COLUMN bid_result_status VARCHAR(32) NULL COMMENT '中标状态',
    ADD COLUMN project_leader_name VARCHAR(100) NULL COMMENT '项目负责人姓名',
    ADD COLUMN leader_department VARCHAR(255) NULL COMMENT '负责人部门';
