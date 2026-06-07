-- U115: 回滚 V115__tender_basic_info_saved_at.sql
-- 移除 tenders.basic_info_saved_at 列

ALTER TABLE tenders
  DROP COLUMN IF EXISTS basic_info_saved_at;
