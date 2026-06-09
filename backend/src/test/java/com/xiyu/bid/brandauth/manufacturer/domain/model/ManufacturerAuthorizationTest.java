package com.xiyu.bid.brandauth.manufacturer.domain.model;

import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AuthStatus;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.ProductLine;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ManufacturerAuthorization domain record.
 * Pure core — no Spring context, no I/O, no side effects.
 */
class ManufacturerAuthorizationTest {

    @Test
    void createManufacturer_shouldSetTypeAndComputeActiveStatus() {
        LocalDate end = LocalDate.now().plusDays(180);
        var auth = ManufacturerAuthorization.create(
                ProductLine.TOOLS, "BR-001", "测试品牌",
                "国产", "测试原厂",
                LocalDate.now(), end, "备注", 1L);

        assertNull(auth.id());
        assertEquals("MANUFACTURER", auth.authorizationType());
        assertEquals(ProductLine.TOOLS, auth.productLine());
        assertEquals("BR-001", auth.brandId());
        assertEquals("测试品牌", auth.brandName());
        assertEquals(AuthStatus.ACTIVE, auth.status());
        assertTrue(auth.canEdit());
        assertFalse(auth.isRevoked());
    }

    @Test
    void createAgent_shouldSetTypeAndAgentName() {
        LocalDate end = LocalDate.now().plusDays(180);
        var auth = ManufacturerAuthorization.createAgent(
                ProductLine.CUTTING_TOOLS, "BR-002", "代理品牌",
                "进口", "原厂A", "代理商X",
                LocalDate.now(), end,
                LocalDate.now(), end.plusDays(30), "一级备注",
                null, null, null,
                "总备注", 2L);

        assertEquals("AGENT", auth.authorizationType());
        assertEquals("代理商X", auth.agentName());
        assertEquals("一级备注", auth.auth1Remarks());
    }

    @Test
    void create_shouldComputeExpiringSoon_whenWithin90Days() {
        LocalDate end = LocalDate.now().plusDays(30);
        var auth = ManufacturerAuthorization.create(
                ProductLine.TOOLS, "BR-003", "即将到期品牌",
                "国产", "原厂B",
                LocalDate.now(), end, null, 1L);

        assertEquals(AuthStatus.EXPIRING_SOON, auth.status());
        assertTrue(auth.isExpiringSoon());
        assertTrue(auth.canEdit());
    }

    @Test
    void create_shouldComputeExpired_whenEndDateBeforeToday() {
        LocalDate end = LocalDate.now().minusDays(1);
        var auth = ManufacturerAuthorization.create(
                ProductLine.TOOLS, "BR-004", "已过期品牌",
                "国产", "原厂C",
                LocalDate.now().minusDays(365), end, null, 1L);

        assertEquals(AuthStatus.EXPIRED, auth.status());
        assertTrue(auth.isExpired());
        assertFalse(auth.canEdit());
        assertTrue(auth.canEditFields());
    }

    @Test
    void withStatus_shouldReturnNewInstanceWithUpdatedStatus() {
        var auth = ManufacturerAuthorization.create(
                ProductLine.TOOLS, "BR-005", "品牌",
                "国产", "原厂D",
                LocalDate.now(), LocalDate.now().plusDays(180), null, 1L);

        var revoked = auth.withStatus(AuthStatus.REVOKED);

        assertEquals(AuthStatus.REVOKED, revoked.status());
        assertTrue(revoked.isRevoked());
        assertFalse(revoked.canEdit());
        // Original unchanged
        assertEquals(AuthStatus.ACTIVE, auth.status());
    }

    @Test
    void withRevokeReason_shouldSetStatusToRevokedAndStoreReason() {
        var auth = ManufacturerAuthorization.create(
                ProductLine.TOOLS, "BR-006", "品牌",
                "国产", "原厂E",
                LocalDate.now(), LocalDate.now().plusDays(180), null, 1L);

        var revoked = auth.withRevokeReason("合同到期终止合作");

        assertEquals(AuthStatus.REVOKED, revoked.status());
        assertEquals("合同到期终止合作", revoked.revokeReason());
    }

    @Test
    void computeRealtimeStatus_shouldReflectCurrentDate() {
        var auth = ManufacturerAuthorization.create(
                ProductLine.TOOLS, "BR-007", "品牌",
                "国产", "原厂F",
                LocalDate.now(), LocalDate.now().plusDays(180), null, 1L);

        assertEquals(AuthStatus.ACTIVE, auth.computeRealtimeStatus());
    }

    @Test
    void createAgent_shouldSupportOptionalSecondAuthPeriod() {
        LocalDate end = LocalDate.now().plusDays(365);
        var auth = ManufacturerAuthorization.createAgent(
                ProductLine.MEASURING_TOOLS, "BR-008", "双期品牌",
                "进口", "原厂G", "代理商Y",
                LocalDate.now(), end,
                LocalDate.now(), end.minusDays(30), "第一期",
                end.minusDays(29), end, "第二期",
                "备注", 3L);

        assertNotNull(auth.auth2StartDate());
        assertEquals("第二期", auth.auth2Remarks());
    }
}
