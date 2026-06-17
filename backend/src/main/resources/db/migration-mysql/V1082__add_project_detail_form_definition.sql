-- 功能：补充 project.detail scope 的表单定义，消除项目详情页 /api/form-definitions/project.detail/active 404
-- 注意：schema_json 使用空 fields，前端会继续渲染 fallback 表单，不改变现有页面行为
INSERT IGNORE INTO form_definition_registry
    (id, scope, scope_label, version, schema_json, enabled, org_id, created_by)
VALUES
    (6, 'project.detail', '项目详情', 1, '{"fields": []}', TRUE, NULL, 'system');
