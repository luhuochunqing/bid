-- Input: migration-mysql/V1169__add_contract_info_to_project_result.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway rollback coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- 回滚 V1169：删除 project_result 表的合同信息两列
-- 注意：回滚会丢失已登记的合同信息数据，生产环境回滚前请先备份
ALTER TABLE project_result
    DROP COLUMN service_period_end_date,
    DROP COLUMN service_period_years;
