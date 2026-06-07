package com.xiyu.bid.brandauth.manufacturer.domain.service;

import com.xiyu.bid.brandauth.manufacturer.domain.model.ManufacturerAuthorization;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AuthStatus;

import java.time.LocalDate;

public final class AuthorizationExpiryPolicy {

    private static final int EXPIRING_SOON_DAYS = 90;

    private AuthorizationExpiryPolicy() {}

    public static AuthStatus evaluate(LocalDate endDate) {
        if (endDate.isBefore(LocalDate.now())) return AuthStatus.EXPIRED;
        if (!endDate.isAfter(LocalDate.now().plusDays(EXPIRING_SOON_DAYS))) return AuthStatus.EXPIRING_SOON;
        return AuthStatus.ACTIVE;
    }

    public static AuthStatus refreshStatus(ManufacturerAuthorization auth) {
        if (auth.status() == AuthStatus.REVOKED || auth.status() == AuthStatus.DRAFT) {
            return auth.status();
        }
        return evaluate(auth.authEndDate());
    }
}
