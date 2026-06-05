package com.xiyu.bid.marketprediction.domain;

import java.time.LocalDate;

/**
 * 商机时间预测结果
 *
 * @param nextTenderDate 预测的下次招标日期
 * @param confidence 预测置信度 (0.0-1.0)
 * @param note 预测说明
 * @param historicalCount 历史数据条数
 * @param averageIntervalDays 历史平均招标间隔(天)
 */
public record MarketPredictionResult(
    LocalDate nextTenderDate,
    double confidence,
    String note,
    int historicalCount,
    double averageIntervalDays
) {
    public static MarketPredictionResult insufficientData() {
        return new MarketPredictionResult(
            null,
            0.0,
            "历史数据不足，无法进行预测",
            0,
            0.0
        );
    }

    public static MarketPredictionResult fromInterval(
            LocalDate baseDate,
            double intervalDays,
            double confidence,
            int historicalCount,
            String note) {
        return new MarketPredictionResult(
            baseDate.plusDays((long) intervalDays),
            confidence,
            note,
            historicalCount,
            intervalDays
        );
    }
}
