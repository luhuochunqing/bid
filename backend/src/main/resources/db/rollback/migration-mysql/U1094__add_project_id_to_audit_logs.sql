-- Input: V1094__add_project_id_to_audit_logs.sql
-- Rollback: 移除 project_id 列与索引。

DROP INDEX idx_audit_project ON audit_logs;
ALTER TABLE audit_logs DROP COLUMN project_id;
