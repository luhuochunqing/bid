package com.xiyu.bid.bidresult.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BidResultCompetitorReportRowDTO {
    private String company;
    private long skuCount;
    private String category;
    private String discount;
    private String paymentTerms;
    private String winRate;
    private long projectCount;
    private String trend;
}
