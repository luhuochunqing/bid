package com.xiyu.bid.scoreanalysis.core;

import com.xiyu.bid.scoreanalysis.RiskLevel;
import com.xiyu.bid.scoreanalysis.dto.DimensionScoreDTO;
import com.xiyu.bid.scoreanalysis.entity.DimensionScore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScoreAnalysisCalculationPolicyTest {

    private ScoreAnalysisCalculationPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new ScoreAnalysisCalculationPolicy();
    }

    @Test
    @DisplayName("加权计算 DTO: 正常加权求和")
    void weightedScoreDTO_Normal_CalculatesCorrectly() {
        List<DimensionScoreDTO> dimensions = List.of(
                dimensionDTO(90, new BigDecimal("0.4")),
                dimensionDTO(80, new BigDecimal("0.3")),
                dimensionDTO(70, new BigDecimal("0.3"))
        );
        assertEquals(new BigDecimal("81.0"), policy.calculateWeightedScoreFromDTOs(dimensions));
    }

    @Test
    @DisplayName("加权计算 DTO: 权重之和不为 1 时归一化")
    void weightedScoreDTO_UnevenWeights_Normalizes() {
        List<DimensionScoreDTO> dimensions = List.of(
                dimensionDTO(80, new BigDecimal("0.3")),
                dimensionDTO(70, new BigDecimal("0.2"))
        );
        assertEquals(new BigDecimal("76.00"), policy.calculateWeightedScoreFromDTOs(dimensions));
    }

    @Test
    @DisplayName("加权计算 DTO: 空列表返回 0")
    void weightedScoreDTO_EmptyList_ReturnsZero() {
        assertEquals(BigDecimal.ZERO, policy.calculateWeightedScoreFromDTOs(List.of()));
    }

    @Test
    @DisplayName("加权计算 DTO: 所有权重为 0 时不除")
    void weightedScoreDTO_ZeroWeight_DoesNotDivide() {
        List<DimensionScoreDTO> dimensions = List.of(
                dimensionDTO(80, BigDecimal.ZERO),
                dimensionDTO(70, BigDecimal.ZERO)
        );
        assertEquals(BigDecimal.ZERO, policy.calculateWeightedScoreFromDTOs(dimensions));
    }

    @Test
    @DisplayName("加权计算 DTO: 权重之和为 1 时不归一化")
    void weightedScoreDTO_TotalWeightOne_DoesNotNormalize() {
        List<DimensionScoreDTO> dimensions = List.of(
                dimensionDTO(85, new BigDecimal("0.5")),
                dimensionDTO(75, new BigDecimal("0.5"))
        );
        assertEquals(new BigDecimal("80.0"), policy.calculateWeightedScoreFromDTOs(dimensions));
    }

    @Test
    @DisplayName("加权计算 Entity: 正常加权求和")
    void weightedScoreEntity_Normal_CalculatesCorrectly() {
        List<DimensionScore> dimensions = List.of(
                dimensionEntity(90, new BigDecimal("0.4")),
                dimensionEntity(80, new BigDecimal("0.3")),
                dimensionEntity(70, new BigDecimal("0.3"))
        );
        assertEquals(new BigDecimal("81.0"), policy.calculateWeightedScoreFromEntities(dimensions));
    }

    @Test
    @DisplayName("加权计算 Entity: 空列表返回 0")
    void weightedScoreEntity_EmptyList_ReturnsZero() {
        assertEquals(BigDecimal.ZERO, policy.calculateWeightedScoreFromEntities(List.of()));
    }

    @Test
    @DisplayName("风险等级: >= 80 → LOW")
    void riskLevel_HighScore_ReturnsLow() {
        assertEquals(RiskLevel.LOW, policy.determineRiskLevel(80));
        assertEquals(RiskLevel.LOW, policy.determineRiskLevel(95));
        assertEquals(RiskLevel.LOW, policy.determineRiskLevel(100));
    }

    @Test
    @DisplayName("风险等级: >= 60 且 < 80 → MEDIUM")
    void riskLevel_MediumScore_ReturnsMedium() {
        assertEquals(RiskLevel.MEDIUM, policy.determineRiskLevel(60));
        assertEquals(RiskLevel.MEDIUM, policy.determineRiskLevel(79));
        assertEquals(RiskLevel.MEDIUM, policy.determineRiskLevel(70));
    }

    @Test
    @DisplayName("风险等级: < 60 → HIGH")
    void riskLevel_LowScore_ReturnsHigh() {
        assertEquals(RiskLevel.HIGH, policy.determineRiskLevel(0));
        assertEquals(RiskLevel.HIGH, policy.determineRiskLevel(30));
        assertEquals(RiskLevel.HIGH, policy.determineRiskLevel(59));
    }

    @Test
    @DisplayName("风险等级: null → MEDIUM（默认）")
    void riskLevel_Null_ReturnsMedium() {
        assertEquals(RiskLevel.MEDIUM, policy.determineRiskLevel(null));
    }

    private static DimensionScoreDTO dimensionDTO(int score, BigDecimal weight) {
        DimensionScoreDTO dto = new DimensionScoreDTO();
        dto.setScore(score);
        dto.setWeight(weight);
        return dto;
    }

    private static DimensionScore dimensionEntity(int score, BigDecimal weight) {
        DimensionScore entity = new DimensionScore();
        entity.setScore(score);
        entity.setWeight(weight);
        return entity;
    }
}
