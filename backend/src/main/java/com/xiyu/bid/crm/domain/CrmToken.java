package com.xiyu.bid.crm.domain;

import java.time.Instant;

public record CrmToken(
        String accessToken,
        long expiresInSeconds,
        Instant acquiredAt
) {
    public Instant expiresAt() {
        return acquiredAt.plusSeconds(expiresInSeconds);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt());
    }

    public boolean needsRenewal(int renewBeforeExpiryRatio) {
        long renewalThreshold = expiresInSeconds * renewBeforeExpiryRatio / 100;
        Instant renewAt = expiresAt().minusSeconds(renewalThreshold);
        return Instant.now().isAfter(renewAt);
    }

    @Override
    public String toString() {
        return "CrmToken[accessToken=" + com.xiyu.bid.shared.infrastructure.SensitiveDataMasker.maskToken(accessToken)
                + ", expiresIn=" + expiresInSeconds + "s, acquiredAt=" + acquiredAt + "]";
    }
}
