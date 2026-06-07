-- Input: migration-mysql/V124__crm_project_mapping.sql
-- Output: rollback script for mysql environments; removes crm_project_mapping table.
-- Pos: Flyway down migration coverage for 西域数智化投标管理平台.

DROP TABLE IF EXISTS crm_project_mapping;
