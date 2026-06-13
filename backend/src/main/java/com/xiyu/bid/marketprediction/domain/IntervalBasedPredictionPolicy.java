package com.xiyu.bid.marketprediction.domain;

import com.xiyu.bid.entity.Tender;


import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于历史招标时间间隔分析的预测策略
 *
 * 算法说明:
 * 1. 收集同一业主单位的所有招标记录的发布时间
 * 2. 计算相邻招标之间的时间间隔
 * 3. 使用平均间隔作为预测基础
 * 4. 置信度基于: 历史数据量 + 间隔稳定性(变异系数)
 *
 * Stub 实现 - 实际生产需要接入 AI 模型进行更精准预测
 */
public class IntervalBasedPredictionPolicy implements MarketPredictionPolicy {

    private static final int MIN_HISTORICAL_COUNT = 2;

    @Override
    public MarketPredictionResult predict(List<Tender> historicalTenders) {
        if (historicalTenders == null || historicalTenders.isEmpty()) {
            return MarketPredictionResult.insufficientData();
        }

        // 按发布时间排序
        List<Tender> sortedTenders = historicalTenders.stream()
                .filter(t -> t.getPublishDate() != null)
                .sorted((a, b) -> a.getPublishDate().compareTo(b.getPublishDate()))
                .toList();

        if (sortedTenders.size() < minimumHistoricalCount()) {
            return MarketPredictionResult.insufficientData();
        }

        // 计算间隔
        List<Double> intervals = new ArrayList<>();
        for (int i = 1; i < sortedTenders.size(); i++) {
            long days = ChronoUnit.DAYS.between(
                    sortedTenders.get(i - 1).getPublishDate(),
                    sortedTenders.get(i).getPublishDate()
            );
            intervals.add((double) days);
        }

        if (intervals.isEmpty()) {
            return MarketPredictionResult.insufficientData();
        }

        // 计算平均间隔
        double avgInterval = intervals.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        // 计算标准差和变异系数
        double stdDev = calculateStdDev(intervals);
        double cv = avgInterval > 0 ? stdDev / avgInterval : 0.0;

        // 计算置信度
        double confidence = calculateConfidence(sortedTenders.size(), cv);

        // 使用最后一次招标时间作为基准
        var lastTender = sortedTenders.get(sortedTenders.size() - 1);
        String note = buildNote(sortedTenders.size(), avgInterval, stdDev);

        return MarketPredictionResult.fromInterval(
                lastTender.getPublishDate(),
                avgInterval,
                confidence,
                sortedTenders.size(),
                note
        );
    }

    @Override
    public int minimumHistoricalCount() {
        return MIN_HISTORICAL_COUNT;
    }

    private String buildNote(int count, double avgInterval, double stdDev) {
        if (count < 3) {
            return String.format("基于 %d 条历史数据预测，平均招标间隔约 %.0f 天", count, avgInterval);
        }

        String stability = stdDev / avgInterval < 0.3 ? "规律性强" : "有一定波动";
        return String.format("基于 %d 条历史数据分析，%s，平均间隔约 %.0f 天",
                count, stability, avgInterval);
    }
}
