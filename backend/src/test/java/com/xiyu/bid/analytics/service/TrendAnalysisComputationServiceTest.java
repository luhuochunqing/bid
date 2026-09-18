// Input: 维度/时间维度查询行（含 JOIN 扇出重复行）
// Output: 断言去重后投标数/中标数与下钻 COUNT(DISTINCT p.id) 口径一致；CO-615 非竞品维度中标率 = 中标数/(已中标+未中标)
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

        TrendComputationResult result =
                service.computeDimensionTrend(rows, null);

        assertThat(result.categories()).containsExactly("华东区");
        assertThat(result.bidSeries()).containsExactly(2L);
        assertThat(result.winSeries()).containsExactly(1L);
    }

    @Test
    @DisplayName("投标数口径：进行中项目（投标中/评标中/已立项）也计入，仅中标计入中标数")
    void inProgressProjectsCountIntoBidSeries() {
        // 区域维度全 0 回归：有总部所在地的项目多为进行中状态，此前 WON+LOST 口径漏计
        List<DimensionRow> rows = List.of(
                new DimensionRow("广东省", 1L, Project.Status.BIDDING),
                new DimensionRow("广东省", 2L, Project.Status.EVALUATING),
                new DimensionRow("广东省", 3L, Project.Status.INITIATED),
                new DimensionRow("广东省", 4L, Project.Status.WON),
                new DimensionRow("北京市", 5L, Project.Status.BIDDING)
        );

        TrendComputationResult result =
                service.computeDimensionTrend(rows, null);

        assertThat(result.bidSeries()).containsExactly(4L, 1L);
        assertThat(result.winSeries()).containsExactly(1L, 0L);
    }

    @Test
    @DisplayName("竞品维度下同一项目分属不同竞品分类时各计一次")
    void sameProjectAcrossDifferentCategoriesCountsPerCategory() {
        List<DimensionRow> rows = List.of(
                new DimensionRow("竞品A", 1L, Project.Status.WON),
                new DimensionRow("竞品B", 1L, Project.Status.WON),
                new DimensionRow("竞品A", 1L, Project.Status.WON) // 同分类重复行仍去重
        );

        TrendComputationResult result =
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

        TrendComputationResult result =
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

        TrendComputationResult result =
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

        TrendComputationResult result =
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

        TrendComputationResult result =
                service.computeTimeTrend(rows, LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 11), "week");

        assertThat(result.categories()).containsExactly("2026-W01", "2026-W02");
        assertThat(result.bidSeries()).containsExactly(1L, 1L);
    }

    //===== specs/055 竞品维度「中标数」= 结果确认「是否中标」列口径 =====

    @Test
    @DisplayName("specs/055: 竞品 winCount 按「是否中标」列判定，与项目级 status 解耦")
    void competitorWinCountFollowsNotesNotProjectStatus() {
        // P1 我司 WON 但该竞品标「未中标」→ 不计；P2 我司 LOST 但该竞品标「已中标」→ 计入；
        // trim 前后空白对齐 parseCompetitorWon 口径
        List<DimensionRow> rows = List.of(
                new DimensionRow("竞品A", 1L, Project.Status.WON, "未中标"),
                new DimensionRow("竞品A", 2L, Project.Status.LOST, "已中标"),
                new DimensionRow("竞品A", 3L, Project.Status.WON, "  已中标 "),
                new DimensionRow("竞品A", 4L, Project.Status.ABANDONED, "已中标")
        );

        TrendComputationResult result =
                service.computeCompetitorTrend(rows, null);

        assertThat(result.categories()).containsExactly("竞品A");
        assertThat(result.bidSeries()).containsExactly(4L);
        assertThat(result.winSeries()).containsExactly(3L);
    }

    @Test
    @DisplayName("specs/055: null/空白/历史自由文本一律按未中标处理")
    void competitorWonRejectsNullBlankAndLegacyFreeText() {
        List<DimensionRow> rows = List.of(
                new DimensionRow("竞品A", 1L, Project.Status.WON, (String) null),
                new DimensionRow("竞品A", 2L, Project.Status.LOST, ""),
                new DimensionRow("竞品A", 3L, Project.Status.WON, "   "),
                new DimensionRow("竞品A", 4L, Project.Status.WON, "其他说明：报价偏高一轮")
        );

        TrendComputationResult result =
                service.computeCompetitorTrend(rows, null);

        assertThat(result.bidSeries()).containsExactly(4L);
        assertThat(result.winSeries()).containsExactly(0L);
        assertThat(result.winRateSeries()).containsExactly(0.0);
    }

    @Test
    @DisplayName("specs/055: 同一项目多个竞品各自独立计数互不影响")
    void competitorsInSameProjectCountIndependently() {
        List<DimensionRow> rows = List.of(
                new DimensionRow("竞品A", 1L, Project.Status.WON, "已中标"),
                new DimensionRow("竞品B", 1L, Project.Status.WON, "未中标"),
                new DimensionRow("竞品C", 1L, Project.Status.LOST, "已中标")
        );

        TrendComputationResult result =
                service.computeCompetitorTrend(rows, null);

        assertThat(result.categories()).containsExactly("竞品A", "竞品B", "竞品C");
        assertThat(result.bidSeries()).containsExactly(1L, 1L, 1L);
        assertThat(result.winSeries()).containsExactly(1L, 0L, 1L);
    }

    @Test
    @DisplayName("specs/055: JOIN 扇出重复行去重后不重复计数（脏行冲突取首行）")
    void competitorDuplicateRowsDeduplicatedBeforeCounting() {
        List<DimensionRow> rows = List.of(
                new DimensionRow("竞品A", 1L, Project.Status.WON, "已中标"),
                new DimensionRow("竞品A", 1L, Project.Status.WON, "未中标"),
                new DimensionRow("竞品A", 2L, Project.Status.LOST, "未中标")
        );

        TrendComputationResult result =
                service.computeCompetitorTrend(rows, null);

        assertThat(result.bidSeries()).containsExactly(2L);
        assertThat(result.winSeries()).containsExactly(1L);
    }

    @Test
    @DisplayName("specs/055: 中标率=该竞品对我司中标率；期望分类补零且 0 除保护")
    void competitorWinRateAndExpectedCategoryZeroFill() {
        List<DimensionRow> rows = List.of(
                new DimensionRow("竞品A", 1L, Project.Status.WON, "已中标"),
                new DimensionRow("竞品A", 2L, Project.Status.WON, "未中标"),
                new DimensionRow("竞品A", 3L, Project.Status.LOST, "未中标")
        );

        TrendComputationResult result =
                service.computeCompetitorTrend(rows, List.of("竞品A", "竞品B"));

        assertThat(result.categories()).containsExactly("竞品A", "竞品B");
        assertThat(result.bidSeries()).containsExactly(3L, 0L);
        assertThat(result.winSeries()).containsExactly(1L, 0L);
        assertThat(result.winRateSeries()).containsExactly(33.3, 0.0);
    }

    //===== CO-615 非竞品维度中标率 = 中标数/(已中标+未中标) =====

    @Test
    @DisplayName("CO-615: 维度趋势中标率分母为已中标+未中标，进行中/弃标不计入")
    void dimensionWinRateUsesOutcomeDenominator() {
        List<DimensionRow> rows = List.of(
                new DimensionRow("华东区", 1L, Project.Status.WON),
                new DimensionRow("华东区", 2L, Project.Status.LOST),
                new DimensionRow("华东区", 3L, Project.Status.LOST),
                new DimensionRow("华东区", 4L, Project.Status.BIDDING),
                new DimensionRow("华东区", 5L, Project.Status.ABANDONED)
        );

        TrendComputationResult result =
                service.computeDimensionTrend(rows, null);

        assertThat(result.bidSeries()).containsExactly(5L);
        assertThat(result.winSeries()).containsExactly(1L);
        // 1/(1+2)=33.3；若误用旧的 win/bid 口径会是 20.0
        assertThat(result.winRateSeries()).containsExactly(33.3);
    }

    @Test
    @DisplayName("CO-615: 时间趋势中标率分母为已中标+未中标，0 除槽位返回 0.0")
    void timeWinRateUsesOutcomeDenominator() {
        List<TrendAnalysisQueryService.TimeDimensionRow> rows = List.of(
                new TrendAnalysisQueryService.TimeDimensionRow(2026, 5, 0, 0, 1L, Project.Status.WON),
                new TrendAnalysisQueryService.TimeDimensionRow(2026, 5, 0, 0, 2L, Project.Status.LOST),
                new TrendAnalysisQueryService.TimeDimensionRow(2026, 5, 0, 0, 3L, Project.Status.EVALUATING),
                new TrendAnalysisQueryService.TimeDimensionRow(2026, 6, 0, 0, 4L, Project.Status.BIDDING)
        );

        TrendComputationResult result =
                service.computeTimeTrend(rows, null, null, "month");

        assertThat(result.categories()).containsExactly("2026-05", "2026-06");
        // 2026-05: 1/(1+1)=50.0；2026-06 无已中标/未中标，0 除保护
        assertThat(result.winRateSeries()).containsExactly(50.0, 0.0);
    }

    @Test
    @DisplayName("CO-615: 全进行中分类 0 除保护返回 0.0")
    void winRateZeroOutcomeDenominatorReturnsZero() {
        List<DimensionRow> rows = List.of(
                new DimensionRow("华北区", 1L, Project.Status.BIDDING)
        );

        TrendComputationResult result =
                service.computeDimensionTrend(rows, null);

        assertThat(result.winRateSeries()).containsExactly(0.0);
    }

    @Test
    @DisplayName("CO-615: 项目状态维度（排除项）维持 win/bid 旧口径")
    void projectStatusDimensionKeepsLegacyRate() {
        List<DimensionRow> rows = List.of(
                new DimensionRow("WON", 1L, Project.Status.WON),
                new DimensionRow("WON", 2L, Project.Status.BIDDING)
        );

        TrendComputationResult result =
                service.computeProjectStatusTrend(rows, null);

        // 1/2=50.0（旧口径）；若误用新分母会是 100.0
        assertThat(result.winRateSeries()).containsExactly(50.0);
    }
}
