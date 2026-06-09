package com.xiyu.bid.brandauth.manufacturer.domain.service;

import com.xiyu.bid.brandauth.manufacturer.domain.model.ManufacturerAuthorization;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AuthStatus;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.ProductLine;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AuthorizationExpiryPolicy pure core service.
 */
class AuthorizationExpiryPolicyTest {

    @Test
    void evaluate_shouldReturnExpired_whenEndDateBeforeToday() {
        LocalDate end = LocalDate.now().minusDays(1);
        assertEquals(AuthStatus.EXPIRED, AuthorizationExpiryPolicy.evaluate(end));
    }

    @Test
    void evaluate_shouldReturnExpiringSoon_whenEndDateWithin90Days() {
        LocalDate end = LocalDate.now().plusDays(90);
        assertEquals(AuthStatus.EXPIRING_SOON, AuthorizationExpiryPolicy.evaluate(end));
    }

    @Test
    void evaluate_shouldReturnActive_whenEndDateBeyond90Days() {
        LocalDate end = LocalDate.now().plusDays(91);
        assertEquals(AuthStatus.ACTIVE, AuthorizationExpiryPolicy.evaluate(end));
    }

    @Test
    void evaluate_shouldReturnExpiringSoon_whenEndDateIsToday() {
        LocalDate end = LocalDate.now();
        assertEquals(AuthStatus.EXPIRING_SOON, AuthorizationExpiryPolicy.evaluate(end));
    }

    @Test
    void refreshStatus_shouldPreserveRevokedStatus() {
        var auth = ManufacturerAuthorization.create(
                ProductLine.TOOLS, "BR-001", "品牌",
                "国产", "原厂",
                LocalDate.now(), LocalDate.now().plusDays(180), null, 1L)
                .withStatus(AuthStatus.REVOKED);

        assertEquals(AuthStatus.REVOKED, AuthorizationExpiryPolicy.refreshStatus(auth));
    }

    @Test
    void refreshStatus_shouldPreserveDraftStatus() {
        var auth = ManufacturerAuthorization.create(
                ProductLine.TOOLS, "BR-002", "品牌",
                "国产", "原厂",
                LocalDate.now(), LocalDate.now().plusDays(180), null, 1L)
                .withStatus(AuthStatus.DRAFT);

        assertEquals(AuthStatus.DRAFT, AuthorizationExpiryPolicy.refreshStatus(auth));
    }

    @Test
    void refreshStatus_shouldRecomputeForActiveStatus() {
        var auth = ManufacturerAuthorization.create(
                ProductLine.TOOLS, "BR-003", "品牌",
                "国产", "原厂",
                LocalDate.now(), LocalDate.now().plusDays(30), null, 1L);

        assertEquals(AuthStatus.EXPIRING_SOON, AuthorizationExpiryPolicy.refreshStatus(auth));
    }
}
