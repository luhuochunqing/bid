-- 回滚 CO-464: 撤销 tenders.purchaser_id 字段
ALTER TABLE tenders DROP COLUMN IF EXISTS purchaser_id;
