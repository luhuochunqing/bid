-- Input: migration-mysql/V1048__brand_auth_operation_log.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- U1048: Drop brand authorization operation log table
DROP TABLE IF EXISTS brand_auth_operation_log;