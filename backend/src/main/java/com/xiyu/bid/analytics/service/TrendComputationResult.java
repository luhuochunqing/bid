// Input: 分类序列与投标数/中标数/中标率序列
// Output: 不可变趋势计算结果载体
// Pos: DTO/analytics 趋势结果（TrendSeriesAssembler 与 TrendAnalysisComputationService 共用）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.analytics.service;

import java.util.List;

public record TrendComputationResult(
        List<String> categories,
        List<Long> bidSeries,
        List<Long> winSeries,
        List<Double> winRateSeries
) {
}
