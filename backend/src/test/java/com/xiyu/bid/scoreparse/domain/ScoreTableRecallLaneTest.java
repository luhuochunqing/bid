// Input: ScoreTableRecallLane 表格召回门面
// Output: 启发式、自定义 SchemaMapper 及降级行为单测
// Pos: Test/scoreparse/domain
package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.TableColumnMapping;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreTableRecallLaneTest {

    private static final String TABLE_MARKDOWN = """
            | 序号 | 评分项目 | 评分标准 | 分值 |
            | --- | --- | --- | --- |
            | 1 | 商务部分 | 1. 财务状况良好得10分 | 10 |
            | 2 | 技术方案 | 2. 方案详细可行得20分 | 20 |
            """;

    @Test
    void recall_withNullOrBlank_returnsEmpty() {
        assertThat(ScoreTableRecallLane.recall(null)).isEmpty();
        assertThat(ScoreTableRecallLane.recall("")).isEmpty();
        assertThat(ScoreTableRecallLane.recall("   ")).isEmpty();
    }

    @Test
    void recall_withDefaultHeuristic_extractsCandidates() {
        List<ScoreCandidate> candidates = ScoreTableRecallLane.recall(TABLE_MARKDOWN);
        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).weight()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(candidates.get(1).weight()).isEqualByComparingTo(new BigDecimal("20"));
    }

    @Test
    void recall_withCustomSchemaMapper_usesProvidedMapping() {
        List<ScoreCandidate> candidates = ScoreTableRecallLane.recall(TABLE_MARKDOWN, grid ->
                new TableColumnMapping(0, -1, 1, 2, 3, 1, false));

        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).code()).isEqualTo("1");
        assertThat(candidates.get(0).dim()).isEqualTo("商务部分");
    }

    @Test
    void recall_whenCustomMapperReturnsNull_fallsBackToHeuristic() {
        List<ScoreCandidate> candidates = ScoreTableRecallLane.recall(TABLE_MARKDOWN, grid -> null);
        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).weight()).isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    void recall_whenCustomMapperThrows_fallsBackToHeuristic() {
        List<ScoreCandidate> candidates = ScoreTableRecallLane.recall(TABLE_MARKDOWN, grid -> {
            throw new RuntimeException("LLM error");
        });
        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).weight()).isEqualByComparingTo(new BigDecimal("10"));
    }
}
