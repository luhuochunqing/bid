-- Input: 回滚脚本参数、当前 DB 状态
-- Output: 成功移除 score_parse_task.run_summary 列
-- Pos: 与 migration-mysql/V1204__add_score_task_run_summary.sql 配对，作为解析运行档案列的回滚
-- 维护声明: 维护者按项目SOP；与 V1204 一起提交，含 header 满足 FlywayRollbackScriptCoverageTest
ALTER TABLE score_parse_task
    DROP COLUMN run_summary;
