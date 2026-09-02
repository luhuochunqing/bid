-- U1206: 回滚 V1206 — 移除 score_parse_task.normalization_warning 列
-- Source: specs/057-score-eval-and-guard/spec.md FR-05
ALTER TABLE score_parse_task
    DROP COLUMN normalization_warning;
