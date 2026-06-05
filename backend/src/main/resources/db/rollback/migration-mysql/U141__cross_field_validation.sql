-- Input: migration-mysql/V141__cross_field_validation.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- ================================================================
-- Rollback: V141__cross_field_validation.sql
-- 说明：删除跨字段验证规则表（cross_field_validation_rule）。
-- ================================================================

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS cross_field_validation_rule;

SET FOREIGN_KEY_CHECKS = 1;
