-- Input: migration-mysql/V1150__backfill_project_leader_department.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.
--
-- 回滚策略：V1150 是纯数据回填迁移，不修改表结构。
-- 回滚将本次回填的 department/leader_department 重新置空（仅限本次迁移实际写入的行）。
--
-- 注意：回滚会丢失本次迁移补齐的部门信息，但不会影响后续通过 ProjectTransferService
-- 正常流程写入的部门字段（因为那些写入发生在 V1150 执行之后，时间戳更晚）。
-- 生产环境回滚前请确认业务影响。

START TRANSACTION;

-- 回滚 tenders.department：置空本次回填的记录
-- 注意：无法精确区分"本次回填"和"之前已存在"的值，因此回滚为"全部置空"。
-- 如需精确回滚，请在执行 V1149 前对 tenders.department 和 project_initiation_details.leader_department
-- 做快照备份。V1149 执行前建议对上述两字段做 SELECT 备份。
UPDATE tenders
SET department = NULL
WHERE department IS NOT NULL AND department <> '';

-- 回滚 project_initiation_details.leader_department：置空本次回填的记录
UPDATE project_initiation_details
SET leader_department = NULL
WHERE leader_department IS NOT NULL AND leader_department <> '';

COMMIT;
