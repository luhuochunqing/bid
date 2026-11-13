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
        Summary summary,
        String qualityStatus,
        com.xiyu.bid.scoreparse.domain.ScoringExtractionReport coverage,
        int objectiveScored, int objectivePending, int subjectivePending, int failed,
        boolean diagnosticsKnown
) {
    public ScoreScoringResultsDTO(List<ScoreResultDTO> results,Summary summary) {
        this(results,summary,"UNKNOWN",null,0,0,0,0,false);
    }
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
            Integer matchRatio,
            String reuseKind,
            String outcome, String reasonCode, Long originTaskId,
            String sourceFileHash, String sourceRuleFingerprint, String sourceEngineVersion,
            String sourceCoverageStatus
    ) {
        public ScoreResultDTO(Long scoreItemId,String code,String dim,String detail,BigDecimal weight,
                String scoreType,String status,BigDecimal actualScore,String evidence,String quote,
                String missedReason,String suggestion,Integer matchRatio,String reuseKind) {
            this(scoreItemId,code,dim,detail,weight,scoreType,status,actualScore,evidence,quote,
                    missedReason,suggestion,matchRatio,reuseKind,null,null,null,null,null,null,null);
        }
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
