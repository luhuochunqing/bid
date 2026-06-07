package com.xiyu.bid.bidresult.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CompetitorWinRequest {
    private Long competitorId;
    private String competitorName;
    private Long projectId;
    private Integer skuCount;
    private String category;
    private String discount;
    private String paymentTerms;
    private LocalDate wonAt;
    private BigDecimal amount;
    private String notes;
}
