// Input: OpportunityPredictionPolicy 提醒规则输入组合
// Output: 4 条预测规则 + 1 年展示窗口 + 90 天临近判断的覆盖
// Pos: backend/src/test/java/com/xiyu/bid/workbench/domain/ - 纯核心策略单测
package com.xiyu.bid.workbench.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OpportunityPredictionPolicy - 商机预测提醒规则")
class OpportunityPredictionPolicyTest {

    private static final LocalDate BID_OPEN = LocalDate.of(2023, 8, 15);

    private static OpportunityPredictionPolicy.Input input(
            String resultType, String customerType, LocalDate bidOpenTime,
            BigDecimal years, LocalDate endDate) {
        return new OpportunityPredictionPolicy.Input(resultType, customerType, bidOpenTime, years, endDate);
    }

    // ==================== 规则 1：有服务周期截止时间 ====================

    @Test
    @DisplayName("央企 + 有截止时间：截止时间提前 6 个月")
    void centralSoeWithEndDateShouldAdvanceSixMonths() {
        var in = input("WON", "CENTRAL_SOE", BID_OPEN, new BigDecimal("5"), LocalDate.of(2026, 8, 14));
        assertEquals(Optional.of(LocalDate.of(2026, 2, 14)), OpportunityPredictionPolicy.predict(in));
    }

    @Test
    @DisplayName("地方国企 + 有截止时间：截止时间提前 3 个月")
    void localSoeWithEndDateShouldAdvanceThreeMonths() {
        var in = input("WON", "LOCAL_SOE", BID_OPEN, new BigDecimal("4"), LocalDate.of(2026, 8, 31));
        assertEquals(Optional.of(LocalDate.of(2026, 5, 31)), OpportunityPredictionPolicy.predict(in));
    }

    @Test
    @DisplayName("民企/未知客户类型 + 有截止时间：按其他类型提前 3 个月")
    void otherCustomerTypeWithEndDateShouldAdvanceThreeMonths() {
        var privateInput = input("WON", "PRIVATE", BID_OPEN, null, LocalDate.of(2026, 12, 4));
        assertEquals(Optional.of(LocalDate.of(2026, 9, 4)), OpportunityPredictionPolicy.predict(privateInput));

        var nullTypeInput = input("WON", null, BID_OPEN, null, LocalDate.of(2026, 12, 4));
        assertEquals(Optional.of(LocalDate.of(2026, 9, 4)), OpportunityPredictionPolicy.predict(nullTypeInput));
    }

    @Test
    @DisplayName("有截止时间时忽略服务周期（年），截止时间规则优先")
    void endDateShouldTakePrecedenceOverYears() {
        var in = input("WON", "LOCAL_SOE", LocalDate.of(2020, 1, 1), new BigDecimal("9"), LocalDate.of(2026, 10, 19));
        assertEquals(Optional.of(LocalDate.of(2026, 7, 19)), OpportunityPredictionPolicy.predict(in));
    }

    // ==================== 规则 2：无截止时间、有服务周期（年） ====================

    @Test
    @DisplayName("无截止时间 + 有服务周期（年）：开标时间 + 服务周期")
    void yearsWithoutEndDateShouldAddToBidOpenTime() {
        var in = input("WON", "LOCAL_SOE", BID_OPEN, new BigDecimal("3"), null);
        assertEquals(Optional.of(LocalDate.of(2026, 8, 15)), OpportunityPredictionPolicy.predict(in));
    }

    @Test
    @DisplayName("小数服务周期（1.5 年）按 18 个月计算")
    void fractionalYearsShouldConvertToMonths() {
        var in = input("WON", "PRIVATE", LocalDate.of(2025, 1, 10), new BigDecimal("1.5"), null);
        assertEquals(Optional.of(LocalDate.of(2026, 7, 10)), OpportunityPredictionPolicy.predict(in));
    }

    @Test
    @DisplayName("服务周期为 0 视为缺失，回退到开标时间 + 1 年")
    void zeroYearsShouldFallbackToOneYear() {
        var in = input("WON", "PRIVATE", BID_OPEN, BigDecimal.ZERO, null);
        assertEquals(Optional.of(LocalDate.of(2024, 8, 15)), OpportunityPredictionPolicy.predict(in));
    }

    @Test
    @DisplayName("脏数据：服务周期超过合理性上限时回退开标时间 + 1 年，不抛异常")
    void unreasonableYearsShouldFallbackToOneYearWithoutThrowing() {
        var huge = input("WON", "PRIVATE", BID_OPEN, new BigDecimal("999999999999"), null);
        assertEquals(Optional.of(LocalDate.of(2024, 8, 15)), OpportunityPredictionPolicy.predict(huge));

        var negative = input("WON", "PRIVATE", BID_OPEN, new BigDecimal("-3"), null);
        assertEquals(Optional.of(LocalDate.of(2024, 8, 15)), OpportunityPredictionPolicy.predict(negative));

        // 上限边界（100 年）仍按规则 2 计算，不抛异常
        var boundary = input("WON", "PRIVATE", LocalDate.of(2026, 1, 1),
                BigDecimal.valueOf(OpportunityPredictionPolicy.MAX_SERVICE_PERIOD_YEARS), null);
        assertEquals(Optional.of(LocalDate.of(2126, 1, 1)), OpportunityPredictionPolicy.predict(boundary));
    }

    // ==================== 规则 3：两者皆无 ====================

    @Test
    @DisplayName("无截止时间、无服务周期：开标时间 + 1 年")
    void neitherEndDateNorYearsShouldAddOneYear() {
        var in = input("WON", "CENTRAL_SOE", BID_OPEN, null, null);
        assertEquals(Optional.of(LocalDate.of(2024, 8, 15)), OpportunityPredictionPolicy.predict(in));
    }

    // ==================== 规则 4：流标 ====================

    @Test
    @DisplayName("流标：开标时间推迟 2 周，且优先于截止时间规则")
    void failedResultShouldPostponeTwoWeeks() {
        var in = input("FAILED", "CENTRAL_SOE", BID_OPEN, new BigDecimal("5"), LocalDate.of(2026, 8, 14));
        assertEquals(Optional.of(LocalDate.of(2023, 8, 29)), OpportunityPredictionPolicy.predict(in));
    }

    // ==================== 缺失输入 ====================

    @Test
    @DisplayName("无开标时间且无截止时间：无法预测，返回空")
    void missingBidOpenTimeAndEndDateShouldBeEmpty() {
        var in = input("WON", "CENTRAL_SOE", null, new BigDecimal("3"), null);
        assertTrue(OpportunityPredictionPolicy.predict(in).isEmpty());

        var failedWithoutOpen = input("FAILED", "CENTRAL_SOE", null, null, null);
        assertTrue(OpportunityPredictionPolicy.predict(failedWithoutOpen).isEmpty());
    }

    // ==================== 展示窗口（近 1 年） ====================

    @Test
    @DisplayName("isVisible：[今天, 今天+12 个月] 边界内可见，边界外/过去不可见")
    void isVisibleShouldRespectOneYearWindow() {
        LocalDate today = LocalDate.of(2026, 8, 18);
        assertTrue(OpportunityPredictionPolicy.isVisible(today, today));
        assertTrue(OpportunityPredictionPolicy.isVisible(today.plusMonths(12), today));
        assertTrue(OpportunityPredictionPolicy.isVisible(today.plusDays(45), today));
        assertFalse(OpportunityPredictionPolicy.isVisible(today.minusDays(1), today));
        assertFalse(OpportunityPredictionPolicy.isVisible(today.plusMonths(12).plusDays(1), today));
        assertFalse(OpportunityPredictionPolicy.isVisible(null, today));
    }

    // ==================== 临近判断（3 个月内红点） ====================

    @Test
    @DisplayName("isNear：90 天内为临近，超过 90 天或已过去不算")
    void isNearShouldUseNinetyDayThreshold() {
        LocalDate today = LocalDate.of(2026, 8, 18);
        assertTrue(OpportunityPredictionPolicy.isNear(today.plusDays(90), today));
        assertTrue(OpportunityPredictionPolicy.isNear(today, today));
        assertFalse(OpportunityPredictionPolicy.isNear(today.plusDays(91), today));
        assertFalse(OpportunityPredictionPolicy.isNear(today.minusDays(1), today));
        assertFalse(OpportunityPredictionPolicy.isNear(null, today));
    }
}
