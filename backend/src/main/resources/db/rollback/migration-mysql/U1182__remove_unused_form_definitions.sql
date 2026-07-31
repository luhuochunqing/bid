-- ============================================================
-- U1182: 回滚 V1182 - 恢复被删除的 knowledge.case / resource.expense 表单定义
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
--     knowledge.case / resource.expense 的规则，回滚后需要手动重新配置。
--   - 回滚也不会恢复 form_submission_audit 记录：审计日志是历史事实快照，
--     V140 种子未预置、正常路径也不产生（前端无可达入口，handleCase 永远返回 failure），
--     故无恢复对象；即使 V1182 曾删过残留 audit，那也是废弃 scope 的无效记录，
--     不应通过回滚重建。
--   - 这种设计是合理的：knowledge.case 后端 FormSubmissionRouter.handleCase()
--     返回 failure "尚未实现"（无真实业务逻辑），前端 src/views/ 无引用；
--     resource.expense 前端侧边栏菜单无入口、工作台快捷入口被 dynamicLayout=null 门控
--     永远不渲染、费用页面走独立 REST，管理员不应为它配置过表单规则，即使有也是误操作。
-- ============================================================
-- Data rollback required: 幂等恢复 V140 种子数据
-- ============================================================
-- 幂等性说明：
--   form_definition_registry 有 uk_scope_org (scope, org_id) 复合唯一键，
--   但本脚本插入的 org_id=NULL，而 MySQL InnoDB 对 NULL 不去重（NULL != NULL），
--   导致 INSERT IGNORE 的唯一键冲突检测失效，重复执行会插入重复记录。
--   因此先显式 DELETE 清理可能存在的残留（WHERE org_id IS NULL），
--   再 INSERT，确保重复回滚也幂等。
-- ============================================================

DELETE FROM form_definition_registry
WHERE scope IN ('knowledge.case', 'resource.expense') AND org_id IS NULL;

INSERT IGNORE INTO form_definition_registry(scope, scope_label, version, schema_json, enabled, org_id, created_by)
VALUES
    ('knowledge.case', '案例建档', 1,
     '{"fields":[{"key":"title","label":"案例标题","type":"TEXT","required":true},{"key":"industry","label":"所属行业","type":"SELECT","required":false,"options":[{"label":"政府","value":"government"},{"label":"央企","value":"soe"},{"label":"民营","value":"private"}]},{"key":"amount","label":"合同金额","type":"CURRENCY","required":false},{"key":"projectDate","label":"完成日期","type":"DATE","required":false},{"key":"description","label":"案例描述","type":"TEXTAREA","required":false,"rows":4},{"key":"tags","label":"标签","type":"TEXT","required":false,"placeholder":"多个标签用逗号分隔"}]}',
     TRUE, NULL, 'system'),
    ('resource.expense', '费用申请', 1,
     '{"fields":[{"key":"projectId","label":"关联项目","type":"PROJECT","required":true},{"key":"category","label":"费用类别","type":"SELECT","required":true,"options":[{"label":"差旅费","value":"travel"},{"label":"办公费","value":"office"},{"label":"咨询费","value":"consulting"},{"label":"其他","value":"other"}]},{"key":"amount","label":"金额","type":"CURRENCY","required":true,"validation":{"min":0.01}},{"key":"expenseDate","label":"费用日期","type":"DATE","required":true},{"key":"description","label":"费用说明","type":"TEXTAREA","required":false,"rows":3}]}',
     TRUE, NULL, 'system');
