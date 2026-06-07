package com.xiyu.bid.tendersource.dto;

import com.xiyu.bid.tendersource.entity.TenderSourceConfig;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 标讯源配置响应 DTO，API 密钥已掩码。
 */
@Getter
public class TenderSourceConfigResponse {

    private final List<String> platforms;
    private final String apiEndpoint;
    private final String apiKeyMasked;
    private final String keywords;
    private final List<String> regions;
    private final BigDecimal budgetMin;
    private final BigDecimal budgetMax;
    private final Boolean autoSync;
    private final Boolean autoDedupe;
    private final String updatedBy;

    public TenderSourceConfigResponse(TenderSourceConfig config) {
        this.platforms = config.getPlatforms();
        this.apiEndpoint = config.getApiEndpoint();
        this.apiKeyMasked = config.getApiKeyEncrypted() != null ? "***" : null;
        this.keywords = config.getKeywords();
        this.regions = config.getRegions();
        this.budgetMin = config.getBudgetMin();
        this.budgetMax = config.getBudgetMax();
        this.autoSync = config.getAutoSync();
        this.autoDedupe = config.getAutoDedupe();
        this.updatedBy = config.getUpdatedBy();
    }
}
