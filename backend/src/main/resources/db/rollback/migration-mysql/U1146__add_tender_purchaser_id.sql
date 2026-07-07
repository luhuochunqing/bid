-- Input: migration-mysql/V1146__add_tender_purchaser_id.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U1146: 回滚 CO-464, 撤销 tenders.purchaser_id 字段
-- 数据丢失说明：purchaser_id 列中已写入的 CRM 招标主体 ID 会丢失，回滚前请确认是否可接受。
ALTER TABLE tenders DROP COLUMN IF EXISTS purchaser_id;
