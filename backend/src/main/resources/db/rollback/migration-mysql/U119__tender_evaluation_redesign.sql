-- Rollback: V119__tender_evaluation_redesign.sql
-- Input: migration-mysql/V119__tender_evaluation_redesign.sql
-- Output: rollback for mysql environments; review data-loss comments before production use.

-- 警告：数据丢失风险
-- 此回滚会删除 V119 新增的列与索引，并还原 V118 的 4 列（数据无法恢复，将以空值还原）。
--
-- H6: 本脚本 **不是幂等的**。Run only once; repeated runs will fail at the
-- first DROP that targets a non-existent column / index. If you need to retry,
-- check `information_schema.COLUMNS` first and trim the corresponding DROP
-- statements by hand, or restore the schema from backup.

ALTER TABLE tender_evaluations
  DROP INDEX idx_tender_eval_status;

ALTER TABLE tender_evaluations
  DROP COLUMN version,
  DROP COLUMN evaluation_status,
  DROP COLUMN project_background,
  DROP COLUMN competitor_analysis,
  DROP COLUMN contract_period_start,
  DROP COLUMN contract_period_end,
  DROP COLUMN shortlisted_count,
  DROP COLUMN platform_service_fee,
  DROP COLUMN previous_quotation,
  DROP COLUMN bid_recommendation,
  DROP COLUMN submitted_at;

ALTER TABLE tender_evaluations
  ADD COLUMN evaluation_content TEXT COMMENT '评估内容' AFTER tender_id,
  ADD COLUMN estimated_budget DECIMAL(19,2) COMMENT '预估预算' AFTER evaluation_content,
  ADD COLUMN risk_assessment VARCHAR(500) COMMENT '风险评估' AFTER estimated_budget,
  ADD COLUMN notes VARCHAR(2000) COMMENT '备注' AFTER risk_assessment;
