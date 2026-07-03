-- U1132__add_has_lease_contract_to_warehouse.sql
-- Input: V1132__add_has_lease_contract_to_warehouse.sql
-- Rollback for V1132__add_has_lease_contract_to_warehouse.sql
-- PR: CO-493
ALTER TABLE warehouse DROP COLUMN IF EXISTS has_lease_contract;
