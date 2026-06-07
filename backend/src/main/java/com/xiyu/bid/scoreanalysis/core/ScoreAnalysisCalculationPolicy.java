package com.xiyu.bid.scoreanalysis.core;

import com.xiyu.bid.scoreanalysis.RiskLevel;
import com.xiyu.bid.scoreanalysis.dto.DimensionScoreDTO;
import com.xiyu.bid.scoreanalysis.entity.DimensionScore;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 评分计算策略 (Pure Core)
 */
public class ScoreAnalysisCalculationPolicy {

    public BigDecimal calculateWeightedScoreFromDTOs(List<DimensionScoreDTO> dimensions) {
        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;

        for (DimensionScoreDTO dimension : dimensions) {
            if (dimension.getScore() != null && dimension.getWeight() != null) {
                totalScore = totalScore.add(BigDecimal.valueOf(dimension.getScore()).multiply(dimension.getWeight()));
                totalWeight = totalWeight.add(dimension.getWeight());
            }
        }

        return normalize(totalScore, totalWeight);
    }

    public BigDecimal calculateWeightedScoreFromEntities(List<DimensionScore> dimensions) {
        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;

        for (DimensionScore dimension : dimensions) {
            if (dimension.getScore() != null && dimension.getWeight() != null) {
                totalScore = totalScore.add(BigDecimal.valueOf(dimension.getScore()).multiply(dimension.getWeight()));
                totalWeight = totalWeight.add(dimension.getWeight());
            }
        }

        return normalize(totalScore, totalWeight);
    }

    public RiskLevel determineRiskLevel(Integer score) {
        if (score == null) return RiskLevel.MEDIUM;
        if (score >= 80) return RiskLevel.LOW;
        if (score >= 60) return RiskLevel.MEDIUM;
        return RiskLevel.HIGH;
    }

    private BigDecimal normalize(BigDecimal totalScore, BigDecimal totalWeight) {
        if (totalWeight.compareTo(BigDecimal.ZERO) > 0 && totalWeight.compareTo(BigDecimal.ONE) != 0) {
            return totalScore.divide(totalWeight, 2, RoundingMode.HALF_UP);
        }
        return totalScore;
    }
}
