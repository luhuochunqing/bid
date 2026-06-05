-- Rollback: V115__tender_filter_fields.sql
-- Input: migration-mysql/V115__tender_filter_fields.sql
-- Output: rollback for mysql environments; review data-loss comments before production use.

-- 警告：数据丢失风险
-- 此回滚会删除 registration_deadline 字段和关联索引

-- 1. 删除索引
DROP INDEX idx_tender_bid_opening_time ON tenders;
DROP INDEX idx_tender_registration_deadline ON tenders;

-- 2. 删除列
ALTER TABLE tenders DROP COLUMN registration_deadline;
