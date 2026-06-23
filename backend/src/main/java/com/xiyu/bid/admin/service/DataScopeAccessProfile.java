package com.xiyu.bid.admin.service;

import lombok.Builder;

import java.util.List;

/**
 * 用户数据范围访问 profile，由 {@link DataScopeConfigService#getAccessProfile} 计算得出。
 */
@Builder
public class DataScopeAccessProfile {
    @Builder.Default
    private String dataScope = "self";
    @Builder.Default
    private List<Long> explicitProjectIds = List.of();
    @Builder.Default
    private List<String> allowedDepartmentCodes = List.of();

    public static DataScopeAccessProfile empty() {
        return DataScopeAccessProfile.builder().build();
    }

    public String getDataScope() {
        return dataScope;
    }

    public List<Long> getExplicitProjectIds() {
        return explicitProjectIds == null ? List.of() : explicitProjectIds;
    }

    public List<String> getAllowedDepartmentCodes() {
        return allowedDepartmentCodes == null ? List.of() : allowedDepartmentCodes;
    }
}
