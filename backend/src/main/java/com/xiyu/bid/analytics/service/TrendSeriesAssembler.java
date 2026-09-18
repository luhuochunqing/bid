// Input: 维度/时间维度聚合桶 + 期望分类
// Output: 排序与补零后的 categories/bid/win/winRate 序列，以及统一的比率计算（1 位小数、0 除保护）
// Pos: analytics 纯计算层辅助（TrendAnalysisComputationService / TrendDrillDownService 共用）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.analytics.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 趋势序列组装与比率计算（纯函数，无副作用，不接触存储/权限）。
 *
 * <p>中标率支持两种分母口径：
 * {@code outcomeDenominator=true} → win/(win+notWon)（维度/时间趋势，CO-615 口径）；
 * {@code outcomeDenominator=false} → win/bid（项目状态维度不展示中标率、竞品维度 specs/055 专用口径）。</p>
 */
final class TrendSeriesAssembler {

    private TrendSeriesAssembler() {
    }

    /** 聚合桶：投标数=全部项目；winCount=已中标；notWonCount=未中标（仅 outcome 分母使用）。 */
    static final class Bucket {
        long bidCount;
        long winCount;
        long notWonCount;
    }

    /** 期望分类补零（数量为 0 也展示）→ 按 bidCount 降序 → 生成四序列。 */
    static TrendAnalysisComputationService.TrendComputationResult assemble(
            Map<String, Bucket> bucketMap, List<String> expectedCategories, boolean outcomeDenominator) {
        if (expectedCategories != null) {
            for (String expected : expectedCategories) {
                bucketMap.computeIfAbsent(expected, ignored -> new Bucket());
            }
        }

        List<Map.Entry<String, Bucket>> sorted = new ArrayList<>(bucketMap.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue().bidCount, a.getValue().bidCount));

        List<String> categories = new ArrayList<>();
        List<Long> bidSeries = new ArrayList<>();
        List<Long> winSeries = new ArrayList<>();
        List<Double> winRateSeries = new ArrayList<>();

        for (Map.Entry<String, Bucket> entry : sorted) {
            Bucket bucket = entry.getValue();
            categories.add(entry.getKey());
            long bid = bucket.bidCount;
            long win = bucket.winCount;
            long notWon = bucket.notWonCount;
            bidSeries.add(bid);
            winSeries.add(win);
            winRateSeries.add(outcomeDenominator ? ratio(win, win + notWon) : ratio(win, bid));
        }

        return new TrendAnalysisComputationService.TrendComputationResult(
                categories, bidSeries, winSeries, winRateSeries);
    }

    /** 百分数比率（1 位小数，0 除保护返回 0.0）。 */
    static double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : Math.round(numerator * 1000.0 / denominator) / 10.0;
    }
}
