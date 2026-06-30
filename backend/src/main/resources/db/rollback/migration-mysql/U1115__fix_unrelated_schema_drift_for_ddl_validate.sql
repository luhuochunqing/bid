-- Input: migration-mysql/V1115__fix_unrelated_schema_drift_for_ddl_validate.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U1115: 回滚 fix_unrelated_schema_drift_for_ddl_validate
-- 回滚会丢失这三列的数据（project_id / crm_opportunity_name / retired），生产回滚前请先备份。
-- 与正向迁移逆序：先 tenders，后 business_qualifications。

ALTER TABLE tenders DROP COLUMN project_id;
ALTER TABLE tenders DROP COLUMN crm_opportunity_name;
ALTER TABLE business_qualifications DROP COLUMN retired;
