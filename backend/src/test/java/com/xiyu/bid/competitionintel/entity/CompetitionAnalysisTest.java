package com.xiyu.bid.competitionintel.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 竞争分析实体测试
 * 测试竞争分析实体的创建、字段验证和业务规则
 */
class CompetitionAnalysisTest {

    @Test
    void createCompetitionAnalysis_WithAllFields_ShouldSucceed() {
        // When
        CompetitionAnalysis analysis = CompetitionAnalysis.builder()
                .id(1L)
                .projectId(100L)
                .competitorId(10L)
                .analysisDate(LocalDateTime.of(2024, 3, 1, 10, 0))
                .winProbability(new BigDecimal("65.5"))
                .competitiveAdvantage("资质齐全，类似项目经验丰富")
                .recommendedStrategy("突出技术优势，适当降低报价")
                .riskFactors("对手可能采取低价策略")
                .build();

        // Then
        assertThat(analysis).isNotNull();
        assertThat(analysis.getId()).isEqualTo(1L);
        assertThat(analysis.getProjectId()).isEqualTo(100L);
        assertThat(analysis.getCompetitorId()).isEqualTo(10L);
        assertThat(analysis.getAnalysisDate()).isEqualTo(LocalDateTime.of(2024, 3, 1, 10, 0));
        assertThat(analysis.getWinProbability()).isEqualByComparingTo("65.5");
        assertThat(analysis.getCompetitiveAdvantage()).isEqualTo("资质齐全，类似项目经验丰富");
        assertThat(analysis.getRecommendedStrategy()).isEqualTo("突出技术优势，适当降低报价");
        assertThat(analysis.getRiskFactors()).isEqualTo("对手可能采取低价策略");
    }

    @Test
    void createCompetitionAnalysis_WithRequiredFieldsOnly_ShouldSucceed() {
        // When
        CompetitionAnalysis analysis = CompetitionAnalysis.builder()
                .projectId(100L)
                .build();

        // Then
        assertThat(analysis).isNotNull();
        assertThat(analysis.getProjectId()).isEqualTo(100L);
        assertThat(analysis.getCompetitorId()).isNull();
        assertThat(analysis.getAnalysisDate()).isNull();
        assertThat(analysis.getWinProbability()).isNull();
        assertThat(analysis.getCompetitiveAdvantage()).isNull();
        assertThat(analysis.getRecommendedStrategy()).isNull();
        assertThat(analysis.getRiskFactors()).isNull();
    }

    @Test
    void settersAndGetters_ShouldWorkCorrectly() {
        // Given
        CompetitionAnalysis analysis = new CompetitionAnalysis();

        // When
        analysis.setId(1L);
        analysis.setProjectId(100L);
        analysis.setCompetitorId(10L);
        analysis.setAnalysisDate(LocalDateTime.of(2024, 3, 1, 10, 0));
        analysis.setWinProbability(new BigDecimal("75.0"));
        analysis.setCompetitiveAdvantage("技术领先");
        analysis.setRecommendedStrategy("强调创新");
        analysis.setRiskFactors("价格竞争");

        // Then
        assertThat(analysis.getId()).isEqualTo(1L);
        assertThat(analysis.getProjectId()).isEqualTo(100L);
        assertThat(analysis.getCompetitorId()).isEqualTo(10L);
        assertThat(analysis.getAnalysisDate()).isEqualTo(LocalDateTime.of(2024, 3, 1, 10, 0));
        assertThat(analysis.getWinProbability()).isEqualByComparingTo("75.0");
        assertThat(analysis.getCompetitiveAdvantage()).isEqualTo("技术领先");
        assertThat(analysis.getRecommendedStrategy()).isEqualTo("强调创新");
        assertThat(analysis.getRiskFactors()).isEqualTo("价格竞争");
    }

    @Test
    void noArgsConstructor_ShouldCreateEmptyAnalysis() {
        // When
        CompetitionAnalysis analysis = new CompetitionAnalysis();

        // Then
        assertThat(analysis).isNotNull();
        assertThat(analysis.getId()).isNull();
        assertThat(analysis.getProjectId()).isNull();
        assertThat(analysis.getCompetitorId()).isNull();
        assertThat(analysis.getAnalysisDate()).isNull();
        assertThat(analysis.getWinProbability()).isNull();
        assertThat(analysis.getCompetitiveAdvantage()).isNull();
        assertThat(analysis.getRecommendedStrategy()).isNull();
        assertThat(analysis.getRiskFactors()).isNull();
    }
}
