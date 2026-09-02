-- V1207: score_parse_task 增加 evidence pipeline Phase 0 可追溯列（spec 059 FR-01）
--
-- 背景：Phase 1-5（PR !2508）已落地结构化提取、归一化显式告警与 LLM prompt 治理，
--   但运行档案（run_summary JSON）混合 lane 贡献/对齐率/告警文本，难以审计
--   "这条 score_item 是哪条链路、何种决策、为何降级"三大问题。
--   Phase 0 引入三列：pipelineVersion（LEGACY_FALLBACK/EVIDENCE_PIPELINE_V1）、
--   decisionStatus（AUTO_APPROVED/REVIEW_REQUIRED/STRUCTURE_INCOMPLETE/PARSE_FAILED）、
--   fallbackReason（RESORT_TO_NORMALIZATION/RESORT_TO_LLM_MERGE/ZERO_VALID_ITEMS 等）。
--   新列全部允许 NULL；Phase 0 不重写历史任务，仅新任务填值，前端 Meta 透出三键。
-- Source: specs/059-score-parse-evidence-pipeline/plan.md FR-01；回滚见 rollback/migration-mysql/U1207
ALTER TABLE score_parse_task
    ADD COLUMN pipeline_version VARCHAR(32) NULL COMMENT '解析 pipeline 标识（LEGACY_FALLBACK / EVIDENCE_PIPELINE_V1，spec 059 Phase 0）' AFTER completed_at,
    ADD COLUMN decision_status  VARCHAR(32) NULL COMMENT '单次任务决策状态（AUTO_APPROVED / REVIEW_REQUIRED / STRUCTURE_INCOMPLETE / PARSE_FAILED）' AFTER pipeline_version,
    ADD COLUMN fallback_reason  VARCHAR(128) NULL COMMENT '旧链路降级原因（RESORT_TO_NORMALIZATION / RESORT_TO_LLM_MERGE / ZERO_VALID_ITEMS 等）' AFTER decision_status;

CREATE INDEX idx_score_parse_task_decision ON score_parse_task (decision_status);