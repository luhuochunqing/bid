-- Input: 回滚脚本参数、当前 DB 状态
-- Output: 成功删除 score_parse_task / score_result / score_item 三表
-- Pos: 与 V1187 配对，作为 AI 评分标准解析后端服务的回滚
-- 维护声明: 维护者按项目SOP；与 V1187 一起提交，含 header 满足 FlywayRollbackScriptCoverageTest
-- Source: V1187__create_score_parse_tables.sql

-- U1187 rollback for V1187__create_score_parse_tables.sql（spec 041 AI 评分标准解析三表）
-- 注意：回滚前请确认无活跃任务（score_parse_task.status IN ('PENDING','PROCESSING')）
--       score_item 与 score_result 为解析产物，回滚即丢弃，重新解析可重建。

DROP TABLE IF EXISTS score_result;
DROP TABLE IF EXISTS score_item;
DROP TABLE IF EXISTS score_parse_task;
