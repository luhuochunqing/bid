package com.xiyu.bid.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * M4 项目模式 — 竞品明细表格行（PRD §9.15）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorTableRowDTO {
    private String competitor;
    private String discount;
    private String paymentDays;
    private Boolean isWon;
}
