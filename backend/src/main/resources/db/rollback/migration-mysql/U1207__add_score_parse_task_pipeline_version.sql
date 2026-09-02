-- Input: 回滚脚本参数、当前 DB 状态
-- Output: 成功移除 score_parse_task.pipeline_version / decision_status / fallback_reason 三列与 idx_score_parse_task_decision 索引
-- Pos: 与 migration-mysql/V1207__add_score_parse_task_pipeline_version.sql 配对，作为 evidence pipeline Phase 0 可追溯列的回滚
-- 维护声明: 维护者按项目SOP；与 V1207 一起提交，含 header 满足 FlywayRollbackScriptCoverageTest
DROP INDEX idx_score_parse_task_decision ON score_parse_task;

ALTER TABLE score_parse_task
    DROP COLUMN fallback_reason,
    DROP COLUMN decision_status,
    DROP COLUMN pipeline_version;