package com.xiyu.bid.marketprediction.domain;

import com.xiyu.bid.entity.Tender;

import java.time.LocalDate;
import java.util.List;

/**
 * 预测策略接口 - 纯核心，不依赖任何框架
 *
 * 定义预测算法的核心逻辑:
 * 1. 收集同一业主单位的历史招标记录
 * 2. 计算招标时间间隔
 * 3. 基于统计模型预测下次招标时间
 */
public interface MarketPredictionPolicy {

    /**
     * 基于历史数据预测下次招标时间
     *
     * @param historicalTenders 同一业主单位的历史招标记录(按时间正序)
     * @return 预测结果
     */
    MarketPredictionResult predict(List<Tender> historicalTenders);

    /**
     * 获取预测所需的最少历史数据条数
     */
    int minimumHistoricalCount();

    /**
     * 计算招标间隔的标准差
     *
     * @param intervals 间隔列表
     * @return 标准差
     */
    default double calculateStdDev(java.util.List<Double> intervals) {
        if (intervals == null || intervals.isEmpty()) {
            return 0.0;
        }

        double sum = intervals.stream().mapToDouble(Double::doubleValue).sum();
        double mean = sum / intervals.size();

        double variance = intervals.stream()
                .mapToDouble(i -> Math.pow(i - mean, 2))
                .sum() / intervals.size();

        return Math.sqrt(variance);
    }

    /**
     * 计算置信度
     * 基于历史数据条数和标准差计算置信度
     *
     * @param historicalCount 历史数据条数
     * @param coefficientOfVariation 变异系数 (标准差/平均值)
     * @return 置信度 (0.0-1.0)
     */
    default double calculateConfidence(int historicalCount, double coefficientOfVariation) {
        // 数据量因子 (最多贡献 0.5)
        double dataFactor = Math.min(0.5, historicalCount * 0.1);

        // 稳定性因子 (最多贡献 0.5)
        // 变异系数越小越稳定，置信度越高
        double stabilityFactor = Math.max(0.0, 0.5 * (1.0 - Math.min(1.0, coefficientOfVariation)));

        return Math.min(0.95, dataFactor + stabilityFactor);
    }
}
