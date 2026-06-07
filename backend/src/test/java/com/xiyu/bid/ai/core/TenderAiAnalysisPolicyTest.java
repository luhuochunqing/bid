package com.xiyu.bid.ai.core;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TenderAiAnalysisPolicyTest {

    @Test
    void evaluate_shouldMapDimensionsRisksAndTasks() {
        TenderAiAnalysisPolicy.AnalysisResult result = TenderAiAnalysisPolicy.evaluate(
            new TenderAiAnalysisPolicy.AnalysisInput(
                84,
                TenderAiAnalysisPolicy.RiskLevel.HIGH,
                List.of(
                    new TenderAiAnalysisPolicy.DimensionRating("Technical", 91),
                    new TenderAiAnalysisPolicy.DimensionRating("Risk", 72)
                ),
                List.of("接口对接不完整", "案例材料较少"),
                List.of("补充接口清单"),
                LocalDate.of(2026, 4, 22)
            )
        );

        assertThat(result.winScore()).isEqualTo(84);
        assertThat(result.suggestion()).isEqualTo("补充接口清单");
        assertThat(result.dimensionScores()).extracting(TenderAiAnalysisPolicy.DimensionRating::dimension)
            .containsExactly("需求匹配", "竞争态势");
        assertThat(result.risks()).hasSize(2);
        assertThat(result.risks().get(0).level()).isEqualTo("high");
        assertThat(result.risks().get(1).level()).isEqualTo("medium");
        assertThat(result.autoTasks()).hasSize(1);
        assertThat(result.autoTasks().get(0).dueDate()).isEqualTo("2026-04-25");
        assertThat(result.autoTasks().get(0).priority()).isEqualTo("high");
    }

    @Test
    void evaluate_shouldUseDefaultSuggestionWhenNoRecommendations() {
        TenderAiAnalysisPolicy.AnalysisResult result = TenderAiAnalysisPolicy.evaluate(
            new TenderAiAnalysisPolicy.AnalysisInput(
                62,
                TenderAiAnalysisPolicy.RiskLevel.MEDIUM,
                List.of(),
                List.of(),
                List.of(),
                LocalDate.of(2026, 4, 22)
            )
        );

        assertThat(result.suggestion()).isEqualTo("建议补强关键短板后继续推进");
        assertThat(result.risks()).isEmpty();
        assertThat(result.autoTasks()).isEmpty();
    }
}
