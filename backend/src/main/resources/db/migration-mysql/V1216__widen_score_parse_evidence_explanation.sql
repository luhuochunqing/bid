-- Input: V1216，score_parse_evidence.explanation 原 VARCHAR(255)，项目 1385 解析长说明
-- Output: 扩为 TEXT，完整保留评分解析证据说明
-- Pos: specs/068-score-adoption-stale-review，修复解析失败导致的采纳状态冲突
-- 原 VARCHAR(255) 会使整个解析事务回滚。
ALTER TABLE score_parse_evidence
    MODIFY COLUMN explanation TEXT NULL COMMENT '解释说明';
