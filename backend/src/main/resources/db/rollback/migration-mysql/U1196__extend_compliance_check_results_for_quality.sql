-- Input: 回滚脚本参数、当前 DB 状态
-- Output: 回滚 compliance_check_results 新增字段与索引
-- Pos: 与 V1196 配对，作为标书质量核查扩展字段的回滚
-- 维护声明: 维护者按项目SOP；与 V1196 一起提交，含 header 满足 FlywayRollbackScriptCoverageTest
-- Source: V1196__extend_compliance_check_results_for_quality.sql

ALTER TABLE compliance_check_results
    DROP INDEX IF EXISTS idx_ccr_hashes,
    DROP INDEX IF EXISTS idx_ccr_task_id,
    DROP COLUMN IF EXISTS total_count,
    DROP COLUMN IF EXISTS manual_review_count,
    DROP COLUMN IF EXISTS failed_count,
    DROP COLUMN IF EXISTS passed_count,
    DROP COLUMN IF EXISTS tender_file_hash,
    DROP COLUMN IF EXISTS bid_file_hash,
    DROP COLUMN IF EXISTS bid_file_name,
    DROP COLUMN IF EXISTS error_message,
    DROP COLUMN IF EXISTS current_step,
    DROP COLUMN IF EXISTS progress,
    DROP COLUMN IF EXISTS status,
    DROP COLUMN IF EXISTS task_id;
