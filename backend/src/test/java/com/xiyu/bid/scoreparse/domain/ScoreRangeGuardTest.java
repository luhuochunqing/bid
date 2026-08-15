package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 得分区间守卫测试（spec 041 FR-016）。
 * <p>得分 ∈ [0, weight] 放行；超区间置 null + invalid 标记（调用方记日志）。
 */
class ScoreRangeGuardTest {

    private final ScoreRangeGuard guard = new ScoreRangeGuard();
    private final BigDecimal weight = new BigDecimal("8");

    @Test
    @DisplayName("区间内得分放行")
    void inRange_passed() {
        ScoreRangeGuard.Result result = guard.guard(new BigDecimal("5"), weight);
        assertThat(result.score()).isEqualByComparingTo("5");
        assertThat(result.valid()).isTrue();
    }

    @Test
    @DisplayName("边界值 0 与满分均放行（闭区间）")
    void boundaryValues_passed() {
        assertThat(guard.guard(BigDecimal.ZERO, weight).valid()).isTrue();
        assertThat(guard.guard(weight, weight).valid()).isTrue();
    }

    @Test
    @DisplayName("负分 → 置 null + invalid（FR-016）")
    void belowZero_nulled() {
        ScoreRangeGuard.Result result = guard.guard(new BigDecimal("-1"), weight);
        assertThat(result.score()).isNull();
        assertThat(result.valid()).isFalse();
    }

    @Test
    @DisplayName("超权重 → 置 null + invalid（FR-016）")
    void aboveWeight_nulled() {
        ScoreRangeGuard.Result result = guard.guard(new BigDecimal("9"), weight);
        assertThat(result.score()).isNull();
        assertThat(result.valid()).isFalse();
    }

    @Test
    @DisplayName("null 输入原样放行（主观项语义）")
    void nullInput_passedThrough() {
        ScoreRangeGuard.Result result = guard.guard(null, weight);
        assertThat(result.score()).isNull();
        assertThat(result.valid()).isTrue();
    }
}
