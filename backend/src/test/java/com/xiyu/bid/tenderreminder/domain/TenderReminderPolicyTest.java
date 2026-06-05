package com.xiyu.bid.tenderreminder.domain;

import com.xiyu.bid.tenderreminder.entity.ReminderType;
import com.xiyu.bid.tenderreminder.entity.TenderReminderSetting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 提醒设置领域策略测试
 */
@DisplayName("TenderReminderPolicy 测试")
class TenderReminderPolicyTest {

    @Nested
    @DisplayName("calculateRemindAt")
    class CalculateRemindAtTests {

        @Test
        @DisplayName("应正确计算提醒时间点")
        void shouldCalculateRemindAtCorrectly() {
            LocalDateTime deadline = LocalDateTime.of(2026, 5, 20, 10, 0);
            int hoursBefore = 24;

            LocalDateTime remindAt = TenderReminderPolicy.calculateRemindAt(deadline, hoursBefore);

            assertEquals(LocalDateTime.of(2026, 5, 19, 10, 0), remindAt);
        }

        @Test
        @DisplayName("deadline为null应返回null")
        void shouldReturnNullWhenDeadlineIsNull() {
            assertNull(TenderReminderPolicy.calculateRemindAt(null, 24));
        }

        @Test
        @DisplayName("应正确计算48小时前提醒")
        void shouldCalculate48HoursBeforeCorrectly() {
            LocalDateTime deadline = LocalDateTime.of(2026, 5, 20, 10, 0);
            int hoursBefore = 48;

            LocalDateTime remindAt = TenderReminderPolicy.calculateRemindAt(deadline, hoursBefore);

            assertEquals(LocalDateTime.of(2026, 5, 18, 10, 0), remindAt);
        }
    }

    @Nested
    @DisplayName("shouldSendReminder")
    class ShouldSendReminderTests {

        @Test
        @DisplayName("设置未启用应返回false")
        void shouldReturnFalseWhenDisabled() {
            TenderReminderSetting setting = TenderReminderSetting.builder()
                    .enabled(false)
                    .remindBeforeHours(24)
                    .build();

            boolean result = TenderReminderPolicy.shouldSendReminder(
                    setting,
                    LocalDateTime.now(),
                    LocalDateTime.now().plusHours(12),
                    null);

            assertFalse(result);
        }

        @Test
        @DisplayName("已发送过应返回false")
        void shouldReturnFalseWhenAlreadyNotified() {
            TenderReminderSetting setting = TenderReminderSetting.builder()
                    .enabled(true)
                    .remindBeforeHours(24)
                    .lastNotifiedAt(LocalDateTime.now().minusHours(1))
                    .build();

            boolean result = TenderReminderPolicy.shouldSendReminder(
                    setting,
                    LocalDateTime.now(),
                    LocalDateTime.now().plusHours(12),
                    LocalDateTime.now().minusHours(1));

            assertFalse(result);
        }

        @Test
        @DisplayName("未到达提醒时间点应返回false")
        void shouldReturnFalseWhenNotYetRemindTime() {
            TenderReminderSetting setting = TenderReminderSetting.builder()
                    .enabled(true)
                    .remindBeforeHours(24)
                    .build();

            // 截止时间还有48小时，当前时间距离截止还有48小时
            LocalDateTime deadline = LocalDateTime.now().plusHours(48);
            LocalDateTime currentTime = LocalDateTime.now();

            boolean result = TenderReminderPolicy.shouldSendReminder(
                    setting,
                    currentTime,
                    deadline,
                    null);

            assertFalse(result);
        }

        @Test
        @DisplayName("已超过截止时间应返回false")
        void shouldReturnFalseWhenPastDeadline() {
            TenderReminderSetting setting = TenderReminderSetting.builder()
                    .enabled(true)
                    .remindBeforeHours(24)
                    .build();

            // 截止时间已过
            LocalDateTime deadline = LocalDateTime.now().minusHours(1);
            LocalDateTime currentTime = LocalDateTime.now();

            boolean result = TenderReminderPolicy.shouldSendReminder(
                    setting,
                    currentTime,
                    deadline,
                    null);

            assertFalse(result);
        }

        @Test
        @DisplayName("满足所有条件应返回true")
        void shouldReturnTrueWhenAllConditionsMet() {
            TenderReminderSetting setting = TenderReminderSetting.builder()
                    .enabled(true)
                    .remindBeforeHours(24)
                    .build();

            // 截止时间还有20小时，当前时间距离截止还有20小时
            LocalDateTime deadline = LocalDateTime.now().plusHours(20);
            LocalDateTime currentTime = LocalDateTime.now();

            boolean result = TenderReminderPolicy.shouldSendReminder(
                    setting,
                    currentTime,
                    deadline,
                    null);

            assertTrue(result);
        }

        @Test
        @DisplayName("setting为null应返回false")
        void shouldReturnFalseWhenSettingIsNull() {
            boolean result = TenderReminderPolicy.shouldSendReminder(
                    null,
                    LocalDateTime.now(),
                    LocalDateTime.now().plusHours(12),
                    null);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("isValidRemindBeforeHours")
    class IsValidRemindBeforeHoursTests {

        @Test
        @DisplayName("1-168之间应返回true")
        void shouldReturnTrueForValidHours() {
            assertTrue(TenderReminderPolicy.isValidRemindBeforeHours(1));
            assertTrue(TenderReminderPolicy.isValidRemindBeforeHours(24));
            assertTrue(TenderReminderPolicy.isValidRemindBeforeHours(48));
            assertTrue(TenderReminderPolicy.isValidRemindBeforeHours(168));
        }

        @Test
        @DisplayName("小于1应返回false")
        void shouldReturnFalseForHoursLessThan1() {
            assertFalse(TenderReminderPolicy.isValidRemindBeforeHours(0));
            assertFalse(TenderReminderPolicy.isValidRemindBeforeHours(-1));
        }

        @Test
        @DisplayName("大于168应返回false")
        void shouldReturnFalseForHoursGreaterThan168() {
            assertFalse(TenderReminderPolicy.isValidRemindBeforeHours(169));
            assertFalse(TenderReminderPolicy.isValidRemindBeforeHours(200));
        }

        @Test
        @DisplayName("null应返回false")
        void shouldReturnFalseForNull() {
            assertFalse(TenderReminderPolicy.isValidRemindBeforeHours(null));
        }
    }

    @Nested
    @DisplayName("getEffectiveRemindBeforeHours")
    class GetEffectiveRemindBeforeHoursTests {

        @Test
        @DisplayName("有效值应直接返回")
        void shouldReturnValueForValidInput() {
            assertEquals(24, TenderReminderPolicy.getEffectiveRemindBeforeHours(24));
            assertEquals(48, TenderReminderPolicy.getEffectiveRemindBeforeHours(48));
        }

        @Test
        @DisplayName("无效值应返回默认值24")
        void shouldReturnDefaultForInvalidInput() {
            assertEquals(24, TenderReminderPolicy.getEffectiveRemindBeforeHours(null));
            assertEquals(24, TenderReminderPolicy.getEffectiveRemindBeforeHours(0));
            assertEquals(24, TenderReminderPolicy.getEffectiveRemindBeforeHours(200));
        }
    }
}
