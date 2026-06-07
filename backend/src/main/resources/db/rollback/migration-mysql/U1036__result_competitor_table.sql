-- Input: migration-mysql/V1036__result_competitor_table.sql
-- Output: rollback script for mysql environments; 删除 project_result_competitor 表及所有关联数据。
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.
DROP TABLE IF EXISTS project_result_competitor;
