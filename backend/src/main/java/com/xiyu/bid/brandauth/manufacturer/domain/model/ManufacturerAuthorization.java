package com.xiyu.bid.brandauth.manufacturer.domain.model;

import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AuthStatus;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.ProductLine;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ManufacturerAuthorization(
        Long id,
        String authorizationType,
        ProductLine productLine,
        String brandId,
        String brandName,
        String importDomestic,
        String manufacturerName,
        String agentName,
        LocalDate authStartDate,
        LocalDate authEndDate,
        LocalDate auth1StartDate,
        LocalDate auth1EndDate,
        String auth1Remarks,
        LocalDate auth2StartDate,
        LocalDate auth2EndDate,
        String auth2Remarks,
        String remarks,
        AuthStatus status,
        String revokeReason,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer version
) {
    public static ManufacturerAuthorization create(
            ProductLine productLine, String brandId, String brandName,
            String importDomestic, String manufacturerName,
            LocalDate authStartDate, LocalDate authEndDate, String remarks, Long createdBy) {
        AuthStatus initialStatus = computeStatus(authEndDate);
        return new ManufacturerAuthorization(null, "MANUFACTURER",
                productLine, brandId, brandName, importDomestic, manufacturerName,
                null, authStartDate, authEndDate, null, null, null, null, null, null,
                remarks, initialStatus, null, createdBy, null, null, 0);
    }

    public static ManufacturerAuthorization createAgent(
            ProductLine productLine, String brandId, String brandName,
            String importDomestic, String manufacturerName, String agentName,
            LocalDate authStartDate, LocalDate authEndDate,
            LocalDate auth1StartDate, LocalDate auth1EndDate, String auth1Remarks,
            LocalDate auth2StartDate, LocalDate auth2EndDate, String auth2Remarks,
            String remarks, Long createdBy) {
        AuthStatus initialStatus = computeStatus(authEndDate);
        return new ManufacturerAuthorization(null, "AGENT",
                productLine, brandId, brandName, importDomestic, manufacturerName,
                agentName, authStartDate, authEndDate,
                auth1StartDate, auth1EndDate, auth1Remarks,
                auth2StartDate, auth2EndDate, auth2Remarks,
                remarks, initialStatus, null, createdBy, null, null, 0);
    }

    public ManufacturerAuthorization withStatus(AuthStatus newStatus) {
        return new ManufacturerAuthorization(id, authorizationType, productLine, brandId, brandName,
                importDomestic, manufacturerName, agentName, authStartDate, authEndDate,
                auth1StartDate, auth1EndDate, auth1Remarks,
                auth2StartDate, auth2EndDate, auth2Remarks,
                remarks, newStatus, revokeReason, createdBy, createdAt, updatedAt, version);
    }

    public ManufacturerAuthorization withRevokeReason(String reason) {
        return new ManufacturerAuthorization(id, authorizationType, productLine, brandId, brandName,
                importDomestic, manufacturerName, agentName, authStartDate, authEndDate,
                auth1StartDate, auth1EndDate, auth1Remarks,
                auth2StartDate, auth2EndDate, auth2Remarks,
                remarks, AuthStatus.REVOKED, reason, createdBy, createdAt, updatedAt, version);
    }

    public AuthStatus computeRealtimeStatus() { return computeStatus(authEndDate); }

    public boolean isExpiringSoon() {
        LocalDate threshold = LocalDate.now().plusDays(90);
        return !authEndDate.isBefore(LocalDate.now()) && !authEndDate.isAfter(threshold);
    }

    public boolean isExpired() { return authEndDate.isBefore(LocalDate.now()); }

    public boolean canEdit() {
        return status == AuthStatus.ACTIVE || status == AuthStatus.EXPIRING_SOON;
    }

    public boolean canEditFields() {
        return status == AuthStatus.ACTIVE || status == AuthStatus.EXPIRING_SOON || status == AuthStatus.EXPIRED;
    }

    public boolean isRevoked() { return status == AuthStatus.REVOKED; }

    private static AuthStatus computeStatus(LocalDate endDate) {
        LocalDate now = LocalDate.now();
        if (endDate.isBefore(now)) return AuthStatus.EXPIRED;
        if (!endDate.isAfter(now.plusDays(90))) return AuthStatus.EXPIRING_SOON;
        return AuthStatus.ACTIVE;
    }
}
