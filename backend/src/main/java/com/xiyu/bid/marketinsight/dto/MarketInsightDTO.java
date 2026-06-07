package com.xiyu.bid.marketinsight.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 市场洞察聚合 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketInsightDTO {

    private List<IndustryTrendDTO> industryTrends;

    private List<PurchaserPatternDTO> purchaserPatterns;

    private List<ForecastTipDTO> forecastTips;

    /**
     * 返回一个所有列表均为空的空实例。
     */
    public static MarketInsightDTO empty() {
        return MarketInsightDTO.builder()
                .industryTrends(new ArrayList<>())
                .purchaserPatterns(new ArrayList<>())
                .forecastTips(new ArrayList<>())
                .build();
    }
}
