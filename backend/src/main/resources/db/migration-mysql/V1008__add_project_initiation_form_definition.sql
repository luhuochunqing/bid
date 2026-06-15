-- PR: !629 后续修复
-- 功能：补充 project.initiation scope 的表单定义，使 /api/form-definitions/project.initiation/active 返回 200
-- 注意：schema_json 使用空 fields，前端会继续渲染 fallback 表单，不改变现有页面行为
INSERT IGNORE INTO form_definition_registry
    (id, scope, scope_label, version, schema_json, enabled, org_id, created_by)
VALUES
    (5, 'project.initiation', '项目立项', 1, '{"fields": []}', TRUE, NULL, 'system');
