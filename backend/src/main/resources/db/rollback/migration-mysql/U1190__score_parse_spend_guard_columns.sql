-- Input: 回滚脚本参数、当前 DB 状态
-- Output: 成功删除 score_parse_task 花费守卫列与 score_result.reuse_kind
-- Pos: 与 V1190 配对，作为评分解析花费守卫列的回滚
-- 维护声明: 维护者按项目SOP；与 V1190 一起提交，含 header 满足 FlywayRollbackScriptCoverageTest
-- Source: V1190__score_parse_spend_guard_columns.sql

-- U1190 rollback for V1190__score_parse_spend_guard_columns.sql（spec 044 花费守卫列）
-- 注意：回滚会丢弃触发来源、投标/评分项指纹、章节哈希和沿用标记，回滚前请确认无进行中的评分解析任务。

ALTER TABLE score_result DROP COLUMN reuse_kind;
ALTER TABLE score_parse_task
    DROP COLUMN chapter_hashes,
    DROP COLUMN item_set_hash,
    DROP COLUMN bid_content_hash,
    DROP COLUMN trigger_source;
