-- V1031: 蓝图 §3.3.1.1 项目立项 - 补齐缺失字段
-- 新增列：年度电商采购额、是否需要保证金、招标文件不利项、风险预判、兜底方案、
--         项目经理了解评标流程、支持及备注、项目计划GAP、总部所在地、AI评估说明

ALTER TABLE project_initiation_details
  ADD COLUMN annual_ecommerce_amount DECIMAL(20,2) DEFAULT NULL COMMENT '年度电商采购额(万)' AFTER annual_revenue,
  ADD COLUMN need_deposit VARCHAR(16) DEFAULT 'NO' COMMENT '是否需要保证金(YES/NO)' AFTER deposit_payment_method,
  ADD COLUMN tender_adverse_items TEXT DEFAULT NULL COMMENT '招标文件不利项' AFTER competitors,
  ADD COLUMN risk_assessment TEXT DEFAULT NULL COMMENT '风险预判（举例说明）' AFTER tender_adverse_items,
  ADD COLUMN risk_mitigation_plan TEXT DEFAULT NULL COMMENT '针对风险的兜底方案' AFTER risk_assessment,
  ADD COLUMN pm_understands_process VARCHAR(16) DEFAULT NULL COMMENT '项目经理是否了解评标全流程(YES/NO)' AFTER risk_mitigation_plan,
  ADD COLUMN support_needed TEXT DEFAULT NULL COMMENT '需要的支持及其他关键信息备注' AFTER pm_understands_process,
  ADD COLUMN project_plan_gap TEXT DEFAULT NULL COMMENT '项目计划GAP说明' AFTER support_needed,
  ADD COLUMN headquarters_location VARCHAR(255) DEFAULT NULL COMMENT '总部所在地' AFTER leader_department,
  ADD COLUMN ai_risk_assessment_notes TEXT DEFAULT NULL COMMENT 'AI风险评估说明' AFTER ai_risk_level;
