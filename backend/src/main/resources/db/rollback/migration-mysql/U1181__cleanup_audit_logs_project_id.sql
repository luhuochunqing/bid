-- Input: V1181__cleanup_audit_logs_project_id.sql
-- 回滚 V1181: cleanup audit_logs.project_id pollution
-- 注意：V1181 将非项目核心 entityType 的 audit_logs.project_id 置 NULL，
-- 此操作不可逆（原值已丢失）。本回滚脚本仅占位，无法恢复原值。
-- 如需回滚，应从备份恢复 audit_logs 表。
-- No-op rollback
SELECT 1;
