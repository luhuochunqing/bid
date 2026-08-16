-- Input: 回滚脚本参数、当前 DB 状态
-- Output: 成功删除 performance_record.contract_amount 列
-- Pos: 与 V1188 配对，作为业绩记录合同金额列的回滚
-- 维护声明: 维护者按项目SOP；与 V1188 一起提交，含 header 满足 FlywayRollbackScriptCoverageTest
-- Source: V1188__add_contract_amount_to_performance_record.sql

-- U1188 rollback for V1188__add_contract_amount_to_performance_record.sql（spec 041 业绩合同金额列）
-- 注意：回滚会丢弃已录入的合同金额数据，回滚前请确认业务允许。

ALTER TABLE performance_record DROP COLUMN contract_amount;
