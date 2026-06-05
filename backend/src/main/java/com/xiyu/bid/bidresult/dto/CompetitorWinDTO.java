package com.xiyu.bid.bidresult.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class CompetitorWinDTO {
    private Long id;
    private Long competitorId;
    private String competitorName;
    private Long projectId;
    private String projectName;
    private Integer skuCount;
    private String category;
    private String discount;
    private String paymentTerms;
    private LocalDate wonAt;
    private BigDecimal amount;
    private String notes;
    private Long recordedBy;
    private String recordedByName;
}
