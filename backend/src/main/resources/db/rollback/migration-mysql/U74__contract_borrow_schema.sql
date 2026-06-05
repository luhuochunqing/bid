-- Input: migration-mysql/V74__contract_borrow_schema.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

DROP INDEX idx_contract_borrow_events_application ON contract_borrow_events;
DROP INDEX idx_contract_borrow_borrower ON contract_borrow_applications;
DROP INDEX idx_contract_borrow_expected_return ON contract_borrow_applications;
DROP INDEX idx_contract_borrow_status ON contract_borrow_applications;
DROP TABLE IF EXISTS contract_borrow_events;
DROP TABLE IF EXISTS contract_borrow_applications;
