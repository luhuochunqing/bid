-- Input: V1026__tender_evaluation_fields_redesign.sql
-- Rollback for V1026__tender_evaluation_fields_redesign.sql

-- 1. 恢复 tender_evaluations 中删除的旧字段
ALTER TABLE tender_evaluations ADD COLUMN project_background TEXT NULL COMMENT '项目背景';
ALTER TABLE tender_evaluations ADD COLUMN competitor_analysis TEXT NULL COMMENT '竞争对手情况';
ALTER TABLE tender_evaluations ADD COLUMN contract_period_start DATE NULL COMMENT '项目合同周期起';
ALTER TABLE tender_evaluations ADD COLUMN contract_period_end DATE NULL COMMENT '项目合同周期止';
ALTER TABLE tender_evaluations ADD COLUMN platform_service_fee DECIMAL(19,2) NULL COMMENT '平台服务费（元）';
ALTER TABLE tender_evaluations ADD COLUMN previous_quotation TEXT NULL COMMENT '上一次报价';

-- 2. 恢复 tender_evaluation_basics 重命名的列
ALTER TABLE tender_evaluation_basics
    CHANGE COLUMN planned_shortlisted_count shortlisted_count INT NULL COMMENT '入围家数',
    CHANGE COLUMN mro_office_flow_amount annual_procurement_amount DECIMAL(15,2) NULL COMMENT '年度电商采购金额（万元）',
    CHANGE COLUMN contingency_plan risk_mitigation_plan TEXT NULL COMMENT '针对风险的兜底方案';
