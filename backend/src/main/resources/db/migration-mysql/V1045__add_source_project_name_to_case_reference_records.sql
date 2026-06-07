-- V1045: Add source project name to case reference records
-- §44.1.1.2 案例引用记录 — 来源项目名称字段
ALTER TABLE case_reference_records ADD COLUMN source_project_name VARCHAR(255) DEFAULT NULL COMMENT '引用来源项目名称';
