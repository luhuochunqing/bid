package com.xiyu.bid.brandauth.application.dto;

import com.xiyu.bid.brandauth.domain.valueobject.AuthorizationStatus;

import java.util.List;

public record AuthorizationRiskDTO(
        String brandName,
        AuthorizationStatus status,
        String statusLabel,
        String riskLevel,
        List<String> warnings
) {

    public static AuthorizationRiskDTO passed(String brandName) {
        return new AuthorizationRiskDTO(brandName, AuthorizationStatus.ACTIVE, "有效", "LOW", List.of());
    }

    public static AuthorizationRiskDTO warning(String brandName, AuthorizationStatus status, List<String> warnings) {
        String level = (status == AuthorizationStatus.EXPIRING_SOON) ? "MEDIUM" : "HIGH";
        String label = switch (status) {
            case EXPIRING_SOON -> "即将到期";
            case EXPIRED -> "已过期";
            case ARCHIVED -> "已下架";
            default -> "未知";
        };
        return new AuthorizationRiskDTO(brandName, status, label, level, warnings);
    }
}
