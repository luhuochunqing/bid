// Input: 维度/时间维度查询行（含 JOIN 扇出重复行）
// Output: 断言去重后投标数/中标数与下钻 COUNT(DISTINCT p.id) 口径一致
// Pos: Test/P1-2 维度查询去重单测（纯核心计算服务）
package com.xiyu.bid.analytics.service;

import com.xiyu.bid.entity.Project;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("P1-2 趋势计算去重 — 与下钻 COUNT(DISTINCT) 口径对齐")
class TrendAnalysisComputationServiceTest {

    private final TrendAnalysisComputationService service = new TrendAnalysisComputationService();

    @Test
    @DisplayName("同一分类下重复项目行只计一次（LEFT JOIN 扇出去重）")
    void duplicateRowsInSameCategoryCountOnce() {
        // 项目 1 因 LEFT JOIN 竞品表产生 2 行，项目 2 中标 1 行
        List<DimensionRow> rows = List.of(
                new DimensionRow("华东区", 1L, Project.Status.LOST),
                new DimensionRow("华东区", 1L, Project.Status.LOST),
                new DimensionRow("华东区", 2L, Project.Status.WON)
        );

        TrendAnalysisComputationService.TrendComputationResult result =
                service.computeDimensionTrend(rows, null);

        assertThat(result.categories()).containsExactly("华东区");
        assertThat(result.bidSeries()).containsExactly(2L);
        assertThat(result.winSeries()).containsExactly(1L);
    }

    @Test
    @DisplayName("竞品维度下同一项目分属不同竞品分类时各计一次")
    void sameProjectAcrossDifferentCategoriesCountsPerCategory() {
        List<DimensionRow> rows = List.of(
                new DimensionRow("竞品A", 1L, Project.Status.WON),
                new DimensionRow("竞品B", 1L, Project.Status.WON),
                new DimensionRow("竞品A", 1L, Project.Status.WON) // 同分类重复行仍去重
        );

        TrendAnalysisComputationService.TrendComputationResult result =
                service.computeDimensionTrend(rows, null);

        assertThat(result.categories()).containsExactly("竞品A", "竞品B");
        assertThat(result.bidSeries()).containsExactly(1L, 1L);
        assertThat(result.winSeries()).containsExactly(1L, 1L);
    }

    @Test
    @DisplayName("项目状态维度重复行只计一次")
    void statusDimensionDeduplicatesRows() {
        List<DimensionRow> rows = List.of(
                new DimensionRow("BIDDING", 1L, Project.Status.BIDDING),
                new DimensionRow("BIDDING", 1L, Project.Status.BIDDING)
        );

        TrendAnalysisComputationService.TrendComputationResult result =
                service.computeProjectStatusTrend(rows, null);

        assertThat(result.categories()).containsExactly("BIDDING");
        assertThat(result.bidSeries()).containsExactly(1L);
    }

    @Test
    @DisplayName("时间维度防御式去重：同一项目多行只计一次")
    void timeDimensionDeduplicatesRows() {
        List<TrendAnalysisQueryService.TimeDimensionRow> rows = List.of(
                new TrendAnalysisQueryService.TimeDimensionRow(2026, 3, 0, 0, 1L, Project.Status.WON),
                new TrendAnalysisQueryService.TimeDimensionRow(2026, 3, 0, 0, 1L, Project.Status.WON),
                new TrendAnalysisQueryService.TimeDimensionRow(2026, 3, 0, 0, 2L, Project.Status.LOST)
        );

        TrendAnalysisComputationService.TrendComputationResult result =
                service.computeTimeTrend(rows, null, null, "month");

        assertThat(result.categories()).containsExactly("2026-03");
        assertThat(result.bidSeries()).containsExactly(2L);
        assertThat(result.winSeries()).containsExactly(1L);
    }

    @Test
    @DisplayName("P1-6 周维度：ISO 周 key 与连续周序列口径一致")
    void weekDimensionBuildsIsoWeekKeysWithContinuousAxis() {
        // 2026-01-01（周四）属 ISO 2026-W01；2026-01-09（周五）属 ISO 2026-W02
        List<TrendAnalysisQueryService.TimeDimensionRow> rows = List.of(
                new TrendAnalysisQueryService.TimeDimensionRow(2026, 0, 1, 0, 1L, Project.Status.WON),
                new TrendAnalysisQueryService.TimeDimensionRow(2026, 0, 2, 0, 2L, Project.Status.LOST)
        );

        TrendAnalysisComputationService.TrendComputationResult result =
                service.computeTimeTrend(rows, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 11), "week");

        assertThat(result.categories()).containsExactly("2026-W01", "2026-W02");
        assertThat(result.bidSeries()).containsExactly(1L, 1L);
        assertThat(result.winSeries()).containsExactly(1L, 0L);
    }

    @Test
    @DisplayName("P1-6 周维度：跨年边界按 ISO 周基准年归入下一年 W01")
    void weekDimensionHandlesIsoYearBoundary() {
        // 2025-12-29（周一）属 ISO 2026-W01（周基准年跨年），2026-01-05 属 2026-W02
        List<TrendAnalysisQueryService.TimeDimensionRow> rows = List.of(
                new TrendAnalysisQueryService.TimeDimensionRow(2026, 0, 1, 0, 1L, Project.Status.LOST),
                new TrendAnalysisQueryService.TimeDimensionRow(2026, 0, 2, 0, 2L, Project.Status.WON)
        );

        TrendAnalysisComputationService.TrendComputationResult result =
                service.computeTimeTrend(rows, LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 11), "week");

        assertThat(result.categories()).containsExactly("2026-W01", "2026-W02");
        assertThat(result.bidSeries()).containsExactly(1L, 1L);
    }
}
