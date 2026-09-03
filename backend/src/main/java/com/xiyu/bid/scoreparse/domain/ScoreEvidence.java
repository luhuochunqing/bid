// Input: 单个评分权重的原文证据
// Output: 不可变 ScoreEvidence
// Pos: scoreparse/domain — 纯核心，无框架依赖（spec 059 Phase 2）
package com.xiyu.bid.scoreparse.domain;

/** 可审计的评分证据片段。 */
public record ScoreEvidence(
        Kind kind,
        String quotedText,
        String cellId,
        int startOffset,
        int endOffset,
        String explanation
) {
    public ScoreEvidence {
        kind = kind == null ? Kind.UNSPECIFIED : kind;
        quotedText = quotedText == null ? "" : quotedText;
        cellId = cellId == null ? "" : cellId;
        explanation = explanation == null ? "" : explanation;
    }

    public boolean isLocatable() {
        return !cellId.isBlank()
                && !quotedText.isBlank()
                && startOffset >= 0
                && endOffset > startOffset;
    }

    public enum Kind {
        EXPLICIT_WEIGHT_COLUMN,
        DIMENSION_TITLE_MAX_SCORE,
        FORMULA_MAX_SCORE,
        AWARD_RULE,
        DEDUCTION_RULE,
        UNSPECIFIED
    }
}
