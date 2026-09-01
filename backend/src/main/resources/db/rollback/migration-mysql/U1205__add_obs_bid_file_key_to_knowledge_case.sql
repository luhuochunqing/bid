-- Input: 回滚脚本参数、当前 DB 状态
-- Output: 成功移除 knowledge_case.obs_bid_file_key 列
-- Pos: 与 migration-mysql/V1205__add_obs_bid_file_key_to_knowledge_case.sql 配对，作为历史案例 OBS 原文键列的回滚
-- 维护声明: 维护者按项目SOP；与 V1205 一起提交，含 header 满足 FlywayRollbackScriptCoverageTest
ALTER TABLE knowledge_case
    DROP COLUMN obs_bid_file_key;
