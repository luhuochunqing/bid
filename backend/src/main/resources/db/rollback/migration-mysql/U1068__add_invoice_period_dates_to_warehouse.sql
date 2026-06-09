-- U1068__add_invoice_period_dates_to_warehouse.sql
-- Input: V1068__add_invoice_period_dates_to_warehouse.sql
-- Rollback for V1068__add_invoice_period_dates_to_warehouse.sql
-- PR: !398
-- 回滚: 移除 invoice_period_start / invoice_period_end 列
ALTER TABLE warehouse
    DROP COLUMN IF EXISTS invoice_period_start,
    DROP COLUMN IF EXISTS invoice_period_end;
