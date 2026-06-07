-- Input: V1045__add_source_project_name_to_case_reference_records.sql
-- Rollback for V1045__add_source_project_name_to_case_reference_records.sql
-- §44.1.1.2 案例引用记录 — 来源项目名称字段回滚
ALTER TABLE case_reference_records DROP COLUMN IF EXISTS source_project_name;
