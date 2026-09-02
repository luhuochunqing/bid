-- Input: 回滚脚本参数、当前 DB 状态
-- Output: 成功移除 score_parse_task.normalization_warning 列
-- Pos: 与 migration-mysql/V1206__add_score_task_normalization_warning.sql 配对，作为权重归一化显式告警列（Phase 2 spec 057 FR-05）的回滚
-- 维护声明: 维护者按项目SOP；与 V1206 一起提交，含 header 满足 FlywayRollbackScriptCoverageTest
ALTER TABLE score_parse_task
    DROP COLUMN normalization_warning;
