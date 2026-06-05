-- U1045: Rollback add source project name
-- §44.1.1.2 案例引用记录 — 来源项目名称字段回滚
ALTER TABLE case_reference_records DROP COLUMN IF EXISTS source_project_name;
