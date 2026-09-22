-- Input: V1216__widen_score_parse_evidence_explanation.sql，当前列为 TEXT
-- Output: 仅在所有说明不超过 255 字时恢复 VARCHAR(255)
-- Pos: V1216 配套回滚；超长数据须先处置，不允许静默截断
-- 先加长度约束：若存在超长说明则拒绝回滚，不修改连接级 sql_mode，也不截断数据。
ALTER TABLE score_parse_evidence
    ADD CONSTRAINT ck_score_parse_evidence_explanation_255 CHECK (CHAR_LENGTH(explanation) <= 255);
ALTER TABLE score_parse_evidence
    MODIFY COLUMN explanation VARCHAR(255) NULL COMMENT '解释说明';
ALTER TABLE score_parse_evidence
    DROP CHECK ck_score_parse_evidence_explanation_255;
