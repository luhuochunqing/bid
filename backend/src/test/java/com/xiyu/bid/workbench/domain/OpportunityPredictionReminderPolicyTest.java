// Input: 预测招标时间 / 今天 / 上次提醒时间 组合
// Output: shouldNotify 判定分支覆盖
// Pos: backend/src/test/java/com/xiyu/bid/workbench/domain/ - 纯核心策略单测
package com.xiyu.bid.workbench.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OpportunityPredictionReminderPolicy - 商机预测提醒判定")
class OpportunityPredictionReminderPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 24);

    @Test
    @DisplayName("今天到预测招标时间且从未提醒：应提醒")
    void reachedPredictedAndNeverNotifiedShouldNotify() {
        assertTrue(OpportunityPredictionReminderPolicy.shouldNotify(TODAY, TODAY, null));
    }

    @Test
    @DisplayName("预测时间已过但从未提醒：应提醒（次日补发语义）")
    void overduePredictedAndNeverNotifiedShouldNotify() {
        assertTrue(OpportunityPredictionReminderPolicy.shouldNotify(TODAY.minusDays(1), TODAY, null));
    }

    @Test
    @DisplayName("预测时间未到：不应提醒")
    void futurePredictedShouldNotNotify() {
        assertFalse(OpportunityPredictionReminderPolicy.shouldNotify(TODAY.plusDays(1), TODAY, null));
    }

    @Test
    @DisplayName("已提醒过：即使预测时间已到也不重复提醒")
    void alreadyNotifiedShouldNotNotifyAgain() {
        LocalDateTime lastNotified = LocalDateTime.of(2026, 8, 20, 9, 5);
        assertFalse(OpportunityPredictionReminderPolicy.shouldNotify(TODAY, TODAY, lastNotified));
    }

    @Test
    @DisplayName("预测时间为空：无法判定，不应提醒")
    void nullPredictedShouldNotNotify() {
        assertFalse(OpportunityPredictionReminderPolicy.shouldNotify(null, TODAY, null));
    }

    @Test
    @DisplayName("今天为空：不应提醒")
    void nullTodayShouldNotNotify() {
        assertFalse(OpportunityPredictionReminderPolicy.shouldNotify(TODAY, null, null));
    }
}
