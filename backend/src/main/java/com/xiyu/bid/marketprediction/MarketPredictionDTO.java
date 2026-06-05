package com.xiyu.bid.marketprediction;

import com.xiyu.bid.marketprediction.domain.MarketPredictionResult;
import java.time.LocalDate;

/**
 * 预测数据传输对象
 */
public record MarketPredictionDTO(
    String purchaserHash,
    LocalDate nextTenderDate,
    double confidence,
    String note,
    int historicalCount,
    boolean hasData
) {
    public MarketPredictionDTO(String purchaserHash, MarketPredictionResult result) {
        this(
            purchaserHash,
            result.nextTenderDate(),
            result.confidence(),
            result.note(),
            result.historicalCount(),
            result.nextTenderDate() != null
        );
    }

    public static MarketPredictionDTO noData(String purchaserHash) {
        return new MarketPredictionDTO(
            purchaserHash,
            null,
            0.0,
            "暂无足够历史数据",
            0,
            false
        );
    }
}
