-- Input: migration-mysql/V143__form_submission_audit.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- ================================================================
-- Rollback: V143__form_submission_audit.sql
-- 说明：删除表单提交审计日志表（form_submission_audit）。
-- ================================================================

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS form_submission_audit;

SET FOREIGN_KEY_CHECKS = 1;
