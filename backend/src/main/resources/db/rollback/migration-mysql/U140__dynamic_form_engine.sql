-- Input: migration-mysql/V140__dynamic_form_engine.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- ================================================================
-- Rollback: V140__dynamic_form_engine.sql
-- 说明：删除动态表单引擎核心表（form_definition_registry、
--       form_field_visibility、form_field_condition）。
-- ================================================================

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS form_field_condition;
DROP TABLE IF EXISTS form_field_visibility;
DROP TABLE IF EXISTS form_definition_registry;

SET FOREIGN_KEY_CHECKS = 1;
