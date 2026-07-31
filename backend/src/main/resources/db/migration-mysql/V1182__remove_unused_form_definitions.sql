-- ============================================================
-- V1182: 删除前端无可达入口的废弃表单定义
-- ============================================================
-- 背景：
--   V140 种子数据预注册了多个 scope 的表单定义，经核实以下两个 scope
--   在前端无用户可达入口，属于废弃配置，清理以避免管理员误配置：
--
--   1. knowledge.case（案例建档）
--      - 前端：src/views/ 无任何引用（案例库 /knowledge/case 走独立 REST）
--      - 后端：FormSubmissionRouter.handleCase() 返回 failure "尚未实现"
--      - E2E：无 knowledge.case 表单引擎提交测试
--
--   2. resource.expense（费用申请）
--      - 前端：侧边栏菜单（sidebar-menu.js）无「费用管理」入口
--      - 工作台快捷入口「投标费用申请」受 dynamicLayout 门控，
--        而 dashboard layout API 不存在（404）+ system_settings 表无配置，
--        dynamicLayout 永远为 null，WorkbenchQuickStart 永远不渲染
--      - 项目详情页 ProjectExpenseSummaryCard 跳转 /resource/expense 是孤儿路由
--      - 费用页面 Expense.vue 走独立 REST /api/resources/expenses，
--        不消费 form-definition 表单定义
--      - 唯一真实消费点：E2E form-engine-scope-router.spec.js 的 expense 提交测试
--        （已在本 PR 同步删除）
--
-- 保留：
--   - tender.entry / project.basic / project.initiation / project.detail
--     均有真实前端 AdaptiveFormPage 消费或业务页 fallback 依赖，保留不动
--
-- 影响范围：
--   - form_definition_registry：删除 scope IN ('knowledge.case','resource.expense')
--   - 关联表（form_field_visibility / form_field_condition / cross_field_validation_rule /
--     tenant_form_field_override）：外键带 ON DELETE CASCADE，会随 definition 删除自动清理
--   - form_submission_audit：外键 fk_fsa_def 无 ON DELETE CASCADE（V143 定义），
--     若存在残留 audit 记录，直接删 definition 会被外键约束卡住导致迁移中断。
--     因此本脚本显式先清理它作为防御性兜底。
--     实测基线库两个 scope 的 audit_count 均为 0（前端无可达入口，正常路径不产生 audit），
--     此 DELETE 通常是无害 no-op。
--
-- 幂等性：使用 DELETE 语句天然幂等（不存在则不删除）
-- ============================================================

DELETE FROM form_field_visibility
WHERE definition_id IN (
    SELECT id FROM form_definition_registry
    WHERE scope IN ('knowledge.case', 'resource.expense')
);

DELETE FROM form_field_condition
WHERE definition_id IN (
    SELECT id FROM form_definition_registry
    WHERE scope IN ('knowledge.case', 'resource.expense')
);

DELETE FROM cross_field_validation_rule
WHERE definition_id IN (
    SELECT id FROM form_definition_registry
    WHERE scope IN ('knowledge.case', 'resource.expense')
);

DELETE FROM tenant_form_field_override
WHERE definition_id IN (
    SELECT id FROM form_definition_registry
    WHERE scope IN ('knowledge.case', 'resource.expense')
);

-- form_submission_audit 外键无 CASCADE，必须显式先删（防御性兜底，防止迁移被外键卡住）
DELETE FROM form_submission_audit
WHERE definition_id IN (
    SELECT id FROM form_definition_registry
    WHERE scope IN ('knowledge.case', 'resource.expense')
);

DELETE FROM form_definition_registry
WHERE scope IN ('knowledge.case', 'resource.expense');
