-- ============================================================
-- U1182: 回滚 V1182 - 恢复被删除的 knowledge.case 表单定义
-- ============================================================
-- Input: V1182__remove_unused_form_definitions.sql
--
-- 回滚策略：
--   使用 INSERT IGNORE 幂等恢复 V140 种子数据，不指定 ID（让数据库自增分配）。
--   如果记录已存在（例如重复执行回滚），不会报错。
--
-- 注意：
--   - 不硬编码 ID，避免与生产环境其他记录冲突（数据库自增分配 ID 更安全）
--   - 回滚不会恢复关联表（form_field_visibility 等）的数据，
--     因为 V140 种子数据并未预置关联表记录，生产环境若管理员曾配置过
--     knowledge.case 的规则，回滚后需要手动重新配置。
--   - 这种设计是合理的：knowledge.case 后端 FormSubmissionRouter.handleCase()
--     返回 failure "尚未实现"（无真实业务逻辑），前端 src/views/ 无引用，
--     管理员不应该有为它配置过规则，即使有也是误操作。
-- ============================================================
-- Data rollback required: INSERT IGNORE 恢复 V140 种子数据
-- ============================================================

INSERT IGNORE INTO form_definition_registry(scope, scope_label, version, schema_json, enabled, org_id, created_by)
VALUES
    ('knowledge.case', '案例建档', 1,
     '{"fields":[{"key":"title","label":"案例标题","type":"TEXT","required":true},{"key":"industry","label":"所属行业","type":"SELECT","required":false,"options":[{"label":"政府","value":"government"},{"label":"央企","value":"soe"},{"label":"民营","value":"private"}]},{"key":"amount","label":"合同金额","type":"CURRENCY","required":false},{"key":"projectDate","label":"完成日期","type":"DATE","required":false},{"key":"description","label":"案例描述","type":"TEXTAREA","required":false,"rows":4},{"key":"tags","label":"标签","type":"TEXT","required":false,"placeholder":"多个标签用逗号分隔"}]}',
     TRUE, NULL, 'system');
