-- U1067__add_updated_by_to_warehouse.sql
-- Input: V1067__add_updated_by_to_warehouse.sql
-- Rollback for V1067__add_updated_by_to_warehouse.sql
-- PR: #367
ALTER TABLE warehouse DROP COLUMN IF EXISTS updated_by;
