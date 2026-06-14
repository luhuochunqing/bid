// Input: 蓝图 §4.1.3.8 24h 去重 / 续期窗口 / 提醒策略启用判定
// Output: QualificationExpiryPolicy 纯核心行为单测
// Pos: test/java/.../businessqualification/domain/service - 纯核心单测
// 维护声明: 24h 去重 + 续期窗口 + 提醒策略启用三个判定为 §4.1.3.8 蓝图的硬规则.
package com.xiyu.bid.businessqualification.domain.service;

import com.xiyu.bid.businessqualification.domain.valueobject.QualificationStatus;
import com.xiyu.bid.businessqualification.domain.valueobject.ReminderPolicy;
import com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class QualificationExpiryPolicyTest {

    private final QualificationExpiryPolicy policy = new QualificationExpiryPolicy();

    @Test
    @DisplayName("24h 去重 - 从未提醒过：应提醒")
    void shouldRemindToday_NullLastRemindedAt_ShouldReturnTrue() {
        boolean result = policy.shouldRemindToday(null, LocalDateTime.now());
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("24h 去重 - 1 小时前刚提醒：应跳过")
    void shouldRemindToday_Within24Hours_ShouldReturnFalse() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 7, 10, 0, 0);
        LocalDateTime oneHourAgo = now.minusHours(1);
        boolean result = policy.shouldRemindToday(oneHourAgo, now);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("24h 去重 - 恰好 24 小时前：应提醒（边界）")
    void shouldRemindToday_Exactly24HoursAgo_ShouldReturnTrue() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 7, 10, 0, 0);
        LocalDateTime exactly24hAgo = now.minusHours(24);
        boolean result = policy.shouldRemindToday(exactly24hAgo, now);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("24h 去重 - 25 小时前：应提醒")
    void shouldRemindToday_Over24HoursAgo_ShouldReturnTrue() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 7, 10, 0, 0);
        LocalDateTime twentyFiveHoursAgo = now.minusHours(25);
        boolean result = policy.shouldRemindToday(twentyFiveHoursAgo, now);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("续期窗口 - 剩余 30 天，阈值 90：在窗内")
    void isWithinAlertWindow_Remaining30Days_AlertDays90_ShouldBeTrue() {
        ValidityPeriod period = new ValidityPeriod(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2026, 7, 7)  // 距今 30 天
        );
        boolean result = policy.isWithinAlertWindow(period, LocalDate.of(2026, 6, 7), 90);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("续期窗口 - 续期后剩余 120 天，阈值 90：不在窗内（蓝图：续期后停止）")
    void isWithinAlertWindow_RenewedTo120Days_AlertDays90_ShouldBeFalse() {
        ValidityPeriod period = new ValidityPeriod(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2026, 10, 5)  // 距今 120 天
        );
        boolean result = policy.isWithinAlertWindow(period, LocalDate.of(2026, 6, 7), 90);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("续期窗口 - 剩余 0 天（今天到期）：在窗内")
    void isWithinAlertWindow_ExpiresToday_ShouldBeTrue() {
        ValidityPeriod period = new ValidityPeriod(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2026, 6, 7)
        );
        boolean result = policy.isWithinAlertWindow(period, LocalDate.of(2026, 6, 7), 90);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("续期窗口 - 剩余 -1 天（已过期）：不在窗内")
    void isWithinAlertWindow_AlreadyExpired_ShouldBeFalse() {
        ValidityPeriod period = new ValidityPeriod(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2026, 6, 6)
        );
        boolean result = policy.isWithinAlertWindow(period, LocalDate.of(2026, 6, 7), 90);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("提醒策略 - 启用")
    void isReminderActive_Enabled_ShouldBeTrue() {
        ReminderPolicy policy = new ReminderPolicy(true, 30, null);
        assertThat(this.policy.isReminderActive(policy)).isTrue();
    }

    @Test
    @DisplayName("提醒策略 - 关闭")
    void isReminderActive_Disabled_ShouldBeFalse() {
        ReminderPolicy policy = new ReminderPolicy(false, 30, null);
        assertThat(this.policy.isReminderActive(policy)).isFalse();
    }

    @Test
    @DisplayName("提醒策略 - null：视为关闭")
    void isReminderActive_Null_ShouldBeFalse() {
        assertThat(this.policy.isReminderActive(null)).isFalse();
    }

    @Test
    @DisplayName("资质到期判定 - 剩余 91 天（阈值 90）：应为在库 IN_STOCK（VALID 仅为 deprecated alias）")
    void evaluate_Remaining91Days_ShouldBeValid() {
        ValidityPeriod period = new ValidityPeriod(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2026, 9, 6)  // 距今 91 天
        );
        assertThat(policy.evaluate(period, LocalDate.of(2026, 6, 7))).isEqualTo(QualificationStatus.IN_STOCK);
    }

    @Test
    @DisplayName("资质到期判定 - 剩余 90 天（阈值 90）：应为 EXPIRING")
    void evaluate_Remaining90Days_ShouldBeExpiring() {
        ValidityPeriod period = new ValidityPeriod(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2026, 9, 5)  // 距今 90 天
        );
        assertThat(policy.evaluate(period, LocalDate.of(2026, 6, 7))).isEqualTo(QualificationStatus.EXPIRING);
    }

    @Test
    @DisplayName("资质到期判定 - 剩余 30 天（阈值 90）：应为 EXPIRING")
    void evaluate_Remaining30Days_ShouldBeExpiring() {
        ValidityPeriod period = new ValidityPeriod(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2026, 7, 7)  // 距今 30 天
        );
        assertThat(policy.evaluate(period, LocalDate.of(2026, 6, 7))).isEqualTo(QualificationStatus.EXPIRING);
    }

    @Test
    @DisplayName("资质到期判定 - 已过期：应为 EXPIRED")
    void evaluate_AlreadyExpired_ShouldBeExpired() {
        ValidityPeriod period = new ValidityPeriod(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2026, 6, 6)  // 距今 -1 天
        );
        assertThat(policy.evaluate(period, LocalDate.of(2026, 6, 7))).isEqualTo(QualificationStatus.EXPIRED);
    }

}
