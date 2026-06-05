package com.xiyu.bid.marketinsight.dto.request;

import lombok.Data;

@Data
public class CustomerInsightQuery {
    private String status;
    private String keyword;
    private String salesRep;
    private String region;
    private String industry;
}
