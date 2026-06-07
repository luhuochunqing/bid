package com.xiyu.bid.tendersource.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 标讯源配置保存请求 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderSourceConfigSaveRequest {

    private List<String> platforms;

    private String apiEndpoint;

    private String apiKey;

    private String keywords;

    private List<String> regions;

    private BigDecimal budgetMin;

    private BigDecimal budgetMax;

    private Boolean autoSync;

    private Integer syncIntervalMinutes;

    private Boolean autoDedupe;
}
