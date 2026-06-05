package com.xiyu.bid.scoreanalysis;

import com.xiyu.bid.scoreanalysis.entity.DimensionScore;
import com.xiyu.bid.scoreanalysis.entity.ScoreAnalysis;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ScoreAnalysis entity tests")
class ScoreAnalysisEntityTest {

    @Test
    @DisplayName("应该成功创建ScoreAnalysis实体")
    void shouldCreateScoreAnalysisEntitySuccessfully() {
        ScoreAnalysis analysis = ScoreAnalysis.builder()
                .id(1L)
                .projectId(100L)
                .analysisDate(LocalDateTime.now())
                .overallScore(85)
                .riskLevel(RiskLevel.LOW)
                .analystId(10L)
                .isAiGenerated(true)
                .summary("优秀的技术方案")
                .build();

        assertNotNull(analysis);
        assertEquals(1L, analysis.getId());
        assertEquals(100L, analysis.getProjectId());
        assertEquals(85, analysis.getOverallScore());
        assertEquals(RiskLevel.LOW, analysis.getRiskLevel());
        assertEquals(10L, analysis.getAnalystId());
        assertTrue(analysis.getIsAiGenerated());
        assertEquals("优秀的技术方案", analysis.getSummary());
    }

    @Test
    @DisplayName("应该成功创建DimensionScore实体")
    void shouldCreateDimensionScoreEntitySuccessfully() {
        DimensionScore dimension = DimensionScore.builder()
                .id(1L)
                .analysisId(100L)
                .dimensionName("技术能力")
                .score(90)
                .weight(new BigDecimal("0.30"))
                .comments("技术团队经验丰富")
                .build();

        assertNotNull(dimension);
        assertEquals(1L, dimension.getId());
        assertEquals(100L, dimension.getAnalysisId());
        assertEquals("技术能力", dimension.getDimensionName());
        assertEquals(90, dimension.getScore());
        assertEquals(new BigDecimal("0.30"), dimension.getWeight());
        assertEquals("技术团队经验丰富", dimension.getComments());
    }
}
