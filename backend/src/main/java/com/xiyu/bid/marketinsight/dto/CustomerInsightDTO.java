package com.xiyu.bid.marketinsight.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 客户洞察 DTO — 匹配前端 customerInsights 数据结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerInsightDTO {

    /** 采购人哈希值（作为客户标识） */
    private String customerId;

    private String customerName;

    private String region;

    private String industry;

    private String salesRep;

    private int opportunityScore;

    private String predictedNextWindow;

    /** watch / recommend / converted — 小写供前端使用 */
    private String status;

    private List<String> mainCategories;

    /** 万元单位 */
    private long avgBudget;

    private String cycleType;
}
