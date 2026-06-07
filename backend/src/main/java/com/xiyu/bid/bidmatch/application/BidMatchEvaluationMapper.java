package com.xiyu.bid.bidmatch.application;

import com.xiyu.bid.bidmatch.domain.BidMatchDimensionScore;
import com.xiyu.bid.bidmatch.domain.BidMatchRuleScore;
import com.xiyu.bid.bidmatch.dto.BidMatchEvaluationResponse;
import com.xiyu.bid.bidmatch.infrastructure.persistence.entity.BidMatchScoreEvaluationEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BidMatchEvaluationMapper {

    private final BidMatchJsonCodec jsonCodec;

    public BidMatchEvaluationMapper(BidMatchJsonCodec pJsonCodec) {
        this.jsonCodec = pJsonCodec;
    }

    public BidMatchEvaluationResponse toResponse(
            BidMatchScoreEvaluationEntity entity,
            boolean stale
    ) {
        List<BidMatchDimensionScore> dimensionScores = jsonCodec.fromJsonList(
                entity.getDimensionScoresJson(),
                BidMatchDimensionScore.class
        );
        return new BidMatchEvaluationResponse(
                entity.getId(),
                entity.getTenderId(),
                entity.getModelId(),
                entity.getModelVersionId(),
                entity.getModelVersionNo(),
                entity.getTotalScore(),
                stale,
                entity.getEvidenceFingerprint(),
                entity.getEvidenceJson(),
                entity.getModelSnapshotJson(),
                entity.getEvaluatedAt(),
                dimensionScores.stream().map(this::toDimensionScoreResponse).toList()
        );
    }

    private BidMatchEvaluationResponse.DimensionScoreResponse toDimensionScoreResponse(
            BidMatchDimensionScore score
    ) {
        return new BidMatchEvaluationResponse.DimensionScoreResponse(
                score.code(),
                score.name(),
                score.weight(),
                score.score(),
                score.ruleScores().stream().map(this::toRuleScoreResponse).toList()
        );
    }

    private BidMatchEvaluationResponse.RuleScoreResponse toRuleScoreResponse(BidMatchRuleScore score) {
        return new BidMatchEvaluationResponse.RuleScoreResponse(
                score.code(),
                score.name(),
                score.type().name(),
                score.evidenceKey(),
                score.weight(),
                score.matched(),
                score.status().name(),
                score.score()
        );
    }
}
