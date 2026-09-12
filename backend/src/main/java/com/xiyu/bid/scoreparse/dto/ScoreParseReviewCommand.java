package com.xiyu.bid.scoreparse.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 人工复核提交命令（spec 059 Phase 5 task3）。
 */
public record ScoreParseReviewCommand(
        List<Action> actions,
        String reason
) {
    public record Action(
            String factId,
            String action,
            BigDecimal adjustedWeight,
            String reason
    ) {
    }
}
