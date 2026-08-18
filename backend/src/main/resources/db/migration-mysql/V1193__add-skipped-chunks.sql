-- V1193: score_parse 跳过 chunk 数可见性（spec 046 US3 / FR-005）
-- 说明: 记录 LLM 结构化提取两连败跳过的 chunk 数，透出到清单 Meta 供前端展示
ALTER TABLE score_parse_task
    ADD COLUMN skipped_chunks INT NULL COMMENT 'LLM 提取跳过的 chunk 数（FR-005）';
