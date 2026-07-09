-- Input: V1157__add_unique_index_to_warehouse_name.sql
-- Rollback for V1157__add_unique_index_to_warehouse_name.sql

ALTER TABLE warehouse DROP INDEX uk_warehouse_name;
