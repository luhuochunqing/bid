package com.xiyu.bid.brandauth.domain.model;

import com.xiyu.bid.brandauth.domain.valueobject.AuthorizationScope;
import com.xiyu.bid.brandauth.domain.valueobject.AuthorizationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record BrandAuthorization(
        Long id,
        String brandName,
        String supplierName,
        AuthorizationScope scope,
        String scopeDetail,
        LocalDate startDate,
        LocalDate endDate,
        AuthorizationStatus status,
        String authorizationDocUrl,
        String remarks,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static BrandAuthorization create(
            Long id, String brandName, String supplierName,
            AuthorizationScope scope, String scopeDetail,
            LocalDate startDate, LocalDate endDate,
            String authorizationDocUrl, String remarks
    ) {
        AuthorizationStatus stat = computeStatus(startDate, endDate);
        return new BrandAuthorization(id, brandName, supplierName,
                scope, scopeDetail, startDate, endDate,
                stat, authorizationDocUrl, remarks,
                LocalDateTime.now(), LocalDateTime.now());
    }

    public AuthorizationStatus computeRealtimeStatus() {
        return computeStatus(startDate, endDate);
    }

    public boolean isExpiringSoon(int warningDays) {
        if (endDate == null) return false;
        long remaining = ChronoUnit.DAYS.between(LocalDate.now(), endDate);
        return remaining > 0 && remaining <= warningDays;
    }

    private static AuthorizationStatus computeStatus(LocalDate start, LocalDate end) {
        if (start == null || end == null) return AuthorizationStatus.EXPIRED;
        LocalDate today = LocalDate.now();
        if (today.isAfter(end)) return AuthorizationStatus.EXPIRED;
        if (today.isBefore(start)) return AuthorizationStatus.EXPIRED;
        long remaining = ChronoUnit.DAYS.between(today, end);
        if (remaining <= 30) return AuthorizationStatus.EXPIRING_SOON;
        return AuthorizationStatus.ACTIVE;
    }

    public BrandAuthorization withStatus(AuthorizationStatus newStatus) {
        return new BrandAuthorization(id, brandName, supplierName,
                scope, scopeDetail, startDate, endDate,
                newStatus, authorizationDocUrl, remarks,
                createdAt, LocalDateTime.now());
    }
}
