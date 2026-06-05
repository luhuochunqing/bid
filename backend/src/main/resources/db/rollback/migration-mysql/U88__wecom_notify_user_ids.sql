-- Input: migration-mysql/V88__wecom_notify_user_ids.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

ALTER TABLE wecom_integration DROP COLUMN notify_user_ids;
