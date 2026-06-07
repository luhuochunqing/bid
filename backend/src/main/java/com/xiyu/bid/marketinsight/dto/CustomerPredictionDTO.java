package com.xiyu.bid.marketinsight.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 客户商机预测 DTO — 匹配前端 customerPredictions 数据结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPredictionDTO {

    private Long opportunityId;

    /** 采购人哈希值 */
    private String customerId;

    private String suggestedProjectName;

    private String predictedCategory;

    /** 万元单位 */
    private long predictedBudgetMin;

    /** 万元单位 */
    private long predictedBudgetMax;

    private String predictedWindow;

    /** 0-1，前端乘以 100 用于显示 */
    private double confidence;

    private String reasoningSummary;

    private List<Long> evidenceRecords;

    private Long convertedProjectId;
}
