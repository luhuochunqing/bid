-- Input: 回滚脚本参数、当前 DB 状态
-- Output: 成功移除 score_parse_fact、score_parse_evidence、score_parse_review 表与 score_parse_task / score_item 扩展列
-- Pos: 与 migration-mysql/V1208__score_parse_evidence_pipeline_phase5.sql 配对，作为 evidence pipeline Phase 5 的回滚脚本
-- 维护声明: 维护者按项目SOP；与 V1208 一起提交，含 header 满足 FlywayRollbackScriptCoverageTest

DROP TABLE IF EXISTS score_parse_review;
DROP TABLE IF EXISTS score_parse_evidence;
DROP TABLE IF EXISTS score_parse_fact;

ALTER TABLE score_item
    DROP COLUMN parent_score_item_id,
    DROP COLUMN validation_status,
    DROP COLUMN confidence,
    DROP COLUMN extraction_method,
    DROP COLUMN weight_evidence,
    DROP COLUMN source_cell_ids;

ALTER TABLE score_parse_task
    DROP COLUMN review_reason,
    DROP COLUMN artifact_sha256;
