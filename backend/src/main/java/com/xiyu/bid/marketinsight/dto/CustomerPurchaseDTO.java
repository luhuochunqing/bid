package com.xiyu.bid.marketinsight.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 客户采购记录 DTO — 匹配前端 customerPurchases 数据结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPurchaseDTO {

    /** 标讯 ID */
    private Long recordId;

    /** 采购人哈希值 */
    private String customerId;

    /** 格式: yyyy-MM-dd */
    private String publishDate;

    private String title;

    /** 行业分类 */
    private String category;

    /** 万元单位 */
    private long budget;

    private boolean isKey;

    private List<String> extractedTags;
}
