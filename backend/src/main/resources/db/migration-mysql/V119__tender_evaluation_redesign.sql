-- V119__tender_evaluation_redesign.sql
-- 重新设计 tender_evaluations 表，承载项目评估表 7 字段。
-- V118 创建的字段在 dev/staging 空表、生产未上线，直接替换。

ALTER TABLE tender_evaluations
  DROP COLUMN evaluation_content,
  DROP COLUMN estimated_budget,
  DROP COLUMN risk_assessment,
  DROP COLUMN notes;

ALTER TABLE tender_evaluations
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本' AFTER tender_id,
  ADD COLUMN evaluation_status ENUM('DRAFT','SUBMITTED') NOT NULL DEFAULT 'DRAFT' COMMENT '评估表状态: DRAFT/SUBMITTED' AFTER version,
  ADD COLUMN project_background TEXT COMMENT '项目背景（必填）' AFTER evaluation_status,
  ADD COLUMN competitor_analysis TEXT COMMENT '竞争对手情况（必填）' AFTER project_background,
  ADD COLUMN contract_period_start DATE COMMENT '项目合同周期起（必填）' AFTER competitor_analysis,
  ADD COLUMN contract_period_end DATE COMMENT '项目合同周期止（必填）' AFTER contract_period_start,
  ADD COLUMN shortlisted_count INT COMMENT '入围家数（必填）' AFTER contract_period_end,
  ADD COLUMN platform_service_fee DECIMAL(19,2) COMMENT '平台服务费（元，必填）' AFTER shortlisted_count,
  ADD COLUMN previous_quotation TEXT COMMENT '上一次报价（非必填）' AFTER platform_service_fee,
  ADD COLUMN bid_recommendation ENUM('RECOMMEND','NOT_RECOMMEND') COMMENT '建议是否投标（非必填）' AFTER previous_quotation,
  ADD COLUMN submitted_at DATETIME NULL COMMENT '提交时间' AFTER bid_recommendation,
  ADD INDEX idx_tender_eval_status (evaluation_status);
