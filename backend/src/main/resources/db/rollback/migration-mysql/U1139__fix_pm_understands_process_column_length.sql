-- Input: V1139__fix_pm_understands_process_column_length.sql
-- U1139: 回滚 V1139 — 将 pm_understands_process 改回 VARCHAR(16)
-- 注意：回滚前需确认无超长数据，否则会触发 Data truncation。
ALTER TABLE project_initiation_details MODIFY COLUMN pm_understands_process VARCHAR(16);
