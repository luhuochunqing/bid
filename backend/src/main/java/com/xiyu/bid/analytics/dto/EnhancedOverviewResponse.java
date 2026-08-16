package com.xiyu.bid.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnhancedOverviewResponse {
    private Long totalCount;
    private Long biddingCount;
    private Long wonCount;
    private Long notWonCount;
    private Double winRate;

    // PRD §3.1 同比数据（null 表示去年同期无数据，前端显示「—」）
    private Double totalCountYoy;      // 投标总数同比百分比
    private Double biddingCountYoy;    // 投标中同比百分比
    private Double wonCountYoy;        // 中标数同比百分比
    private Double winRateYoy;         // 中标率同比（百分点差值）

    // 今日新增项目数（投标总数卡片底部显示）
    private Long todayNewCount;
}
