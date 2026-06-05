-- Rollback: V118__tender_evaluation_and_review.sql
-- Input: migration-mysql/V118__tender_evaluation_and_review.sql
-- Output: rollback for mysql environments; review data-loss comments before production use.

-- 警告：数据丢失风险
-- 此回滚会删除 tender_evaluations 表和 abandonment_reason 字段

-- 1. 删除 tender_evaluations 表
DROP TABLE IF EXISTS tender_evaluations;

-- 2. 删除 abandonment_reason 列
ALTER TABLE tenders DROP COLUMN IF EXISTS abandonment_reason;
