-- Input: migration-mysql/V1152__add_last_review_reminded_at.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U1152: 回滚 CO-532 新增的 last_review_reminded_at 列
-- 数据丢失说明：last_review_reminded_at 列中已写入的提醒时间戳会丢失。
ALTER TABLE business_qualifications DROP COLUMN IF EXISTS last_review_reminded_at;
