package com.xiyu.bid.marketinsight.dto;

import com.xiyu.bid.marketinsight.core.TrendAnalysisPolicy;

/**
 * 市场洞察 DTO 组装器。
 * 无状态，无依赖，无副作用。
 */
public final class MarketInsightAssembler {

    private MarketInsightAssembler() {
    }

    /**
     * 将核心趋势结果映射到 DTO。
     *
     * @param result 趋势计算结果
     * @param color  主题色
     * @return 行业趋势 DTO
     */
    public static IndustryTrendDTO toDTO(final TrendAnalysisPolicy.TrendResult result,
                                         final String color) {
        return IndustryTrendDTO.builder()
                .industry(result.industry())
                .count(result.count())
                .amount(result.amount())
                .growth(result.growth())
                .trend(result.trend())
                .hotLevel(result.hotLevel())
                .color(color)
                .build();
    }

    /**
     * 将核心采购人模式结果映射到 DTO。
     *
     * @param result 采购人模式计算结果
     * @return 采购人模式 DTO
     */
    public static PurchaserPatternDTO toDTO(final TrendAnalysisPolicy.PurchaserPatternResult result) {
        return PurchaserPatternDTO.builder()
                .name(result.name())
                .industry(result.industry())
                .frequency(result.frequency())
                .period(result.period())
                .avgBudget(result.avgBudget())
                .opportunity(result.opportunity())
                .build();
    }

    /**
     * 将核心预测提示映射到 DTO。
     *
     * @param tip 预测提示
     * @return 预测提示 DTO
     */
    public static ForecastTipDTO toDTO(final TrendAnalysisPolicy.ForecastTip tip) {
        return ForecastTipDTO.builder()
                .text(tip.text())
                .color(tip.color())
                .build();
    }
}
