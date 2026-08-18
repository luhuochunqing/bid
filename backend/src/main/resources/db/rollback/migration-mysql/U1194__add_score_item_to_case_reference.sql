-- Input: 回滚脚本参数、当前 DB 状态
-- Output: 回滚 case_reference_records 新增字段与索引
-- Pos: 与 V1194 配对，作为案例复用增强的回滚
-- 维护声明: 维护者按项目SOP；与 V1194 一起提交，含 header 满足 FlywayRollbackScriptCoverageTest
-- Source: V1194__add_score_item_to_case_reference.sql

ALTER TABLE case_reference_records
    DROP INDEX IF EXISTS idx_case_ref_score_item,
    DROP INDEX IF EXISTS idx_case_ref_project,
    DROP COLUMN IF EXISTS reuse_mode,
    DROP COLUMN IF EXISTS score_item_id,
    DROP COLUMN IF EXISTS project_id;
