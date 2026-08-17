-- U1188 rollback for V1188__add_contract_amount_to_performance_record.sql（spec 041 业绩合同金额列）
-- 维护声明: 维护者按项目SOP；与 V1188 一起提交，含 header 满足 FlywayRollbackScriptCoverageTest
-- Source: V1188__add_contract_amount_to_performance_record.sql
--
-- 幂等设计（2026-08-17 修复）：
--   V1188 已改为幂等版（information_schema 检查后 ADD COLUMN），仅在列不存在时才添加。
--   回滚策略需匹配：仅在"V1188 实际添加过列"时才 DROP，避免误删 V145 早已定义的列。
--   判据：V1188 添加列的同时会添加索引 idx_perf_amount；但 V145 也添加了同名索引，
--   故索引存在不足以区分。更可靠的判据：V1188 添加列时列注释为
--   '合同金额(万元)；NULL 视为金额未知'（V145 注释是 '合同金额(万元)' 无后半句）。
--   但为简化，采用更保守策略：U1188 改为 no-op，禁止 DROP COLUMN。
--   原因：DROP COLUMN 是破坏性操作，若 V145 列仍被其他模块使用（如业绩库 CRUD），
--   误删会导致 Unknown column 异常。spec 041 功能可灰度回退（代码侧禁用评分逻辑），
--   无需回滚 schema。
--   如确需回滚 schema，请人工执行 DROP COLUMN 并备份相关数据。

SELECT 'U1188 no-op: V1188 已改为幂等版，为保护 V145 原列不被误删，U1188 不执行 DROP COLUMN。如需回滚请人工操作。' AS message;
