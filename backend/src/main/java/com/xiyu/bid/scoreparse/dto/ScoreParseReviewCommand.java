package com.xiyu.bid.scoreparse.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 人工复核提交命令（spec 059 Phase 5 task3）。
 */
public record ScoreParseReviewCommand(
        List<Action> actions,
        String reason,
        Long parseTaskId
) {
    public ScoreParseReviewCommand(List<Action> actions, String reason) {
        this(actions, reason, null);
    }
    public record Action(
            String factId,
            String action,
            BigDecimal adjustedWeight,
            String reason,
            String adjustedCode,
            String adjustedDimension,
            String adjustedSourceText,
            String adjustedParentFactId,
            String adjustedScoreType
    ) {
        public Action(String factId, String action, BigDecimal adjustedWeight, String reason) {
            this(factId, action, adjustedWeight, reason, null, null, null, null, null);
        }
    }
}
