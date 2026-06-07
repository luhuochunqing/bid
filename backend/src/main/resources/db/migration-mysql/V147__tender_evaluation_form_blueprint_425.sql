-- V147: 评估表基础信息添加客户营收字段
-- 对应 PRD §4.2.5 蓝图 - 基础信息第9个字段
--
-- tender_evaluation_basics 表当前有8个字段（来自 V130）：
--   shortlisted_count, annual_procurement_amount, unfavorable_items,
--   risk_assessment, risk_mitigation_plan, process_knowledge,
--   support_notes, project_plan_gap
-- 新增第9字段：customer_revenue

ALTER TABLE tender_evaluation_basics
    ADD COLUMN customer_revenue DECIMAL(15,2) DEFAULT NULL COMMENT '客户营收（万）' AFTER project_plan_gap;
