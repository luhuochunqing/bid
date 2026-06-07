-- Input: migration-mysql/V142__tenant_form_override.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- ================================================================
-- Rollback: V142__tenant_form_override.sql
-- 说明：删除租户表单字段覆盖表（tenant_form_field_override）。
-- ================================================================

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS tenant_form_field_override;

SET FOREIGN_KEY_CHECKS = 1;
