-- ============================================================
-- V1182: 删除前端未使用的表单定义 knowledge.case
-- ============================================================
-- 背景：
--   V140 种子数据预注册了 knowledge.case（案例建档）scope，
--   但在前端代码（src/views/）和后端 FormSubmissionRouter 中均未使用：
--     - 前端：src/views/ 无任何引用
--     - 后端：FormSubmissionRouter.handleCase() 返回 failure "尚未实现"
--     - E2E：无 knowledge.case 相关测试用例
--   作为废弃数据清理，避免管理员在 /settings/workflow-forms 误配置。
--
-- 注意：
--   resource.expense 虽然前端未使用，但后端 FormSubmissionRouter.handleExpense
--   有完整业务实现（expenseService.createExpense）和 E2E 测试覆盖，
--   因此本次保留 resource.expense 表单定义。
--
-- 影响范围：
--   - form_definition_registry：删除 scope='knowledge.case' 的记录
--   - 关联表（form_field_visibility / form_field_condition / cross_field_validation_rule /
--     tenant_form_field_override）：外键带 ON DELETE CASCADE，会随 definition 删除自动清理
--   - form_submission_audit：外键 fk_fsa_def 无 ON DELETE CASCADE（V143 定义），
--     若存在残留 audit 记录，直接删 definition 会被外键约束卡住导致迁移中断。
--     因此本脚本显式先清理它作为防御性兜底。
--     实测基线库 knowledge.case audit_count=0（handleCase 永远返回 failure，
--     且前端无提交入口，正常路径不会产生 audit），此 DELETE 通常是无害 no-op。
--
-- 幂等性：使用 DELETE 语句天然幂等（不存在则不删除）
-- ============================================================

DELETE FROM form_field_visibility
WHERE definition_id IN (
    SELECT id FROM form_definition_registry
    WHERE scope = 'knowledge.case'
);

DELETE FROM form_field_condition
WHERE definition_id IN (
    SELECT id FROM form_definition_registry
    WHERE scope = 'knowledge.case'
);

DELETE FROM cross_field_validation_rule
WHERE definition_id IN (
    SELECT id FROM form_definition_registry
    WHERE scope = 'knowledge.case'
);

DELETE FROM tenant_form_field_override
WHERE definition_id IN (
    SELECT id FROM form_definition_registry
    WHERE scope = 'knowledge.case'
);

-- form_submission_audit 外键无 CASCADE，必须显式先删（防御性兜底，防止迁移被外键卡住）
DELETE FROM form_submission_audit
WHERE definition_id IN (
    SELECT id FROM form_definition_registry
    WHERE scope = 'knowledge.case'
);

DELETE FROM form_definition_registry
WHERE scope = 'knowledge.case';
