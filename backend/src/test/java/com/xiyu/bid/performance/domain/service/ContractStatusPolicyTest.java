package com.xiyu.bid.performance.domain.service;

import com.xiyu.bid.performance.domain.valueobject.ContractStatus;
import com.xiyu.bid.performance.domain.valueobject.CustomerType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ContractStatusPolicy 纯核心策略单元测试
 *
 * 覆盖：
 *   - calculateDaysRemaining: null 边界、正常差值、已过期（负数）
 *   - calculateStatus: null expiryDate → IN_PERFORMANCE、已过期 → EXPIRED、阈值边界
 *   - calculateExpiryReminder: 对应状态文本
 *
 * CO-583 回归：expiryDate 为 null 时 daysRemaining 返回 null（不再返回 Long.MAX_VALUE）
 */
class ContractStatusPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 15);

    @Test
    void calculateDaysRemaining_nullExpiryDate_returnsNull() {
        // CO-583 回归：expiryDate 为 null 返回 null，不再返回 Long.MAX_VALUE
        assertThat(ContractStatusPolicy.calculateDaysRemaining(null, TODAY)).isNull();
    }

    @Test
    void calculateDaysRemaining_nullToday_returnsNull() {
        assertThat(ContractStatusPolicy.calculateDaysRemaining(LocalDate.of(2026, 12, 31), null)).isNull();
    }

    @Test
    void calculateDaysRemaining_futureDate_returnsPositiveDays() {
        long days = ContractStatusPolicy.calculateDaysRemaining(LocalDate.of(2026, 7, 25), TODAY);
        assertThat(days).isEqualTo(10);
    }

    @Test
    void calculateDaysRemaining_pastDate_returnsNegativeDays() {
        long days = ContractStatusPolicy.calculateDaysRemaining(LocalDate.of(2026, 7, 5), TODAY);
        assertThat(days).isEqualTo(-10);
    }

    @Test
    void calculateStatus_nullExpiryDate_returnsInPerformance() {
        // 无截止日期的合同默认"履约中"
        assertThat(ContractStatusPolicy.calculateStatus(CustomerType.PRIVATE_ENTERPRISE, null, TODAY))
                .isEqualTo(ContractStatus.IN_PERFORMANCE);
    }

    @Test
    void calculateStatus_expiredDate_returnsExpired() {
        assertThat(ContractStatusPolicy.calculateStatus(
                CustomerType.PRIVATE_ENTERPRISE, LocalDate.of(2026, 7, 10), TODAY))
                .isEqualTo(ContractStatus.EXPIRED);
    }

    @Test
    void calculateStatus_centralSoeWithin180Days_returnsExpiring() {
        // 央企阈值 180 天：剩余 150 天 → 即将到期
        assertThat(ContractStatusPolicy.calculateStatus(
                CustomerType.CENTRAL_SOE, TODAY.plusDays(150), TODAY))
                .isEqualTo(ContractStatus.EXPIRING);
    }

    @Test
    void calculateStatus_nonCentralSoeWithin90Days_returnsExpiring() {
        // 非央企阈值 90 天：剩余 60 天 → 即将到期
        assertThat(ContractStatusPolicy.calculateStatus(
                CustomerType.PRIVATE_ENTERPRISE, TODAY.plusDays(60), TODAY))
                .isEqualTo(ContractStatus.EXPIRING);
    }

    @Test
    void calculateStatus_nonCentralSoeBeyond90Days_returnsInPerformance() {
        // 非央企剩余 100 天 > 90 天阈值 → 履约中
        assertThat(ContractStatusPolicy.calculateStatus(
                CustomerType.PRIVATE_ENTERPRISE, TODAY.plusDays(100), TODAY))
                .isEqualTo(ContractStatus.IN_PERFORMANCE);
    }

    @Test
    void calculateExpiryReminder_nullExpiryDate_returnsNull() {
        assertThat(ContractStatusPolicy.calculateExpiryReminder(
                CustomerType.PRIVATE_ENTERPRISE, null, TODAY)).isNull();
    }

    @Test
    void calculateExpiryReminder_expiredDate_returnsExpiredText() {
        assertThat(ContractStatusPolicy.calculateExpiryReminder(
                CustomerType.PRIVATE_ENTERPRISE, LocalDate.of(2026, 7, 10), TODAY))
                .isEqualTo("合同已到期");
    }

    @Test
    void calculateExpiryReminder_withinThreshold_returnsExpiringText() {
        assertThat(ContractStatusPolicy.calculateExpiryReminder(
                CustomerType.PRIVATE_ENTERPRISE, TODAY.plusDays(60), TODAY))
                .isEqualTo("合同即将到期");
    }

    @Test
    void calculateExpiryReminder_beyondThreshold_returnsNull() {
        // 剩余 100 天 > 90 天阈值 → 无提醒
        assertThat(ContractStatusPolicy.calculateExpiryReminder(
                CustomerType.PRIVATE_ENTERPRISE, TODAY.plusDays(100), TODAY)).isNull();
    }
}
