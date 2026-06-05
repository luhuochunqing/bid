-- Input: migration-mysql/V134__qualification_alert_config.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

DROP TABLE IF EXISTS qualification_alert_config;
