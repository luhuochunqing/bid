package com.xiyu.bid.scoreparse.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 评分项清单响应（contracts/score-parse-api.md §3）。
 * <p>summary.weightWarning=true 时前端展示实际总分（FR-022）。
 * <p>meta 携带抽屉来源信息栏所需的文件名/时间元数据（无任务时字段为 null，前端显示空态）。
 */
public record ScoreParseItemsDTO(
        List<ScoreItemDTO> items,
        Summary summary,
        Meta meta
) {

    public record Summary(
            BigDecimal totalWeight,
            BigDecimal totalEstScore,
            int okCount,
            int dangerCount,
            int pendingCount,
            BigDecimal objectiveWeight,
            BigDecimal subjectiveWeight,
            boolean weightWarning
    ) {
    }

    /**
     * 来源信息栏元数据：招标文件名/解析时间来自最新快照与最近 PARSE 任务；
     * 投标文件名/评分时间来自最近 SCORING 任务（fileName 即上传的投标文件名）。
     */
    public record Meta(
            String sourceFileName,
            LocalDateTime parseTime,
            String bidFileName,
            LocalDateTime scoreTime
    ) {
    }
}
