package com.xiyu.bid.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * M4 分组模式 — 单个竞品公司的堆叠柱数据（PRD §9.10）。
 * 每个竞品公司一组，组内三段堆叠：minData / avgData / maxData。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorGroupDTO {
    private String competitor;
    private List<Double> minData;
    private List<Double> avgData;
    private List<Double> maxData;
}
