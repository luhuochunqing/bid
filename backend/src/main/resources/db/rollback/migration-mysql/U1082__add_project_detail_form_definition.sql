-- Input: V1082__add_project_detail_form_definition.sql
-- Rollback for V1082__add_project_detail_form_definition.sql
-- Data rollback required: 删除 project.detail scope 的表单定义，恢复为 404 状态（由前端 fallback 表单兜底）

DELETE FROM form_definition_registry
WHERE scope = 'project.detail'
  AND version = 1
  AND created_by = 'system';
