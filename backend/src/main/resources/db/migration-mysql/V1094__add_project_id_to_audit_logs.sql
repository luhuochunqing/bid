-- Purpose: CO-324 项目动态操作日志打通。
--  - audit_logs 加 project_id 列，AuditableAspect 写入时从入参/返回值提取 projectId，
--    使项目详情「项目动态」可按 project_id 查询操作日志。
--  - 历史行 project_id 为 NULL（仅新操作起生效）；新操作由 AuditableAspect 填充。

ALTER TABLE audit_logs ADD COLUMN project_id BIGINT NULL AFTER entity_id;

CREATE INDEX idx_audit_project ON audit_logs (project_id, timestamp);
