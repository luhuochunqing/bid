package com.xiyu.bid.scoreparse.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 阶段 2 打分结果响应（contracts/score-parse-api.md §7）。
 * <p>FR-018：阶段 2 响应不含 kbHit 字段；主观项 actualScore=null；
 * quote=null 时前端显示"标书引用：无"。
 */
public record ScoreScoringResultsDTO(
        List<ScoreResultDTO> results,
        Summary summary
) {

    public record ScoreResultDTO(
            Long scoreItemId,
            String code,
            String dim,
            String detail,
            BigDecimal weight,
            String scoreType,
            String status,
            BigDecimal actualScore,
            String evidence,
            String quote,
            String missedReason,
            String suggestion,
            Integer matchRatio
    ) {
    }

    public record Summary(
            BigDecimal totalWeight,
            BigDecimal totalActualScore,
            int okCount,
            int dangerCount,
            int pendingCount,
            BigDecimal objectiveWeight,
            BigDecimal subjectiveWeight,
            boolean weightWarning
    ) {
    }
}
