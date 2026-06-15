-- Input: V1078__add_project_initiation_form_definition.sql
-- PR: !629 后续修复回滚
-- Data rollback required
-- 删除 project.initiation scope 的表单定义
DELETE FROM form_definition_registry WHERE scope = 'project.initiation' AND id = 5;
