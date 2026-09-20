// Input: 基准秒数与原始文件大小（含边界/未知/自定义基准）
// Output: 验证等待上限分档纯函数（契约值、封顶、单调性）
// Pos: compliance/domain/ - 解析等待上限单测（spec 066 T005）
package com.xiyu.bid.compliance.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SidecarTimeoutBudget 按原始大小分档纯函数")
class SidecarTimeoutBudgetTest {

    private static final long MB = 1024L * 1024L;

    @Test
    @DisplayName("小文件与 50MB 边界走基准档")
    void smallAndBoundary_useBase() {
        assertThat(SidecarTimeoutBudget.secondsFor(60, 10 * MB)).isEqualTo(60);
        assertThat(SidecarTimeoutBudget.secondsFor(60, 50 * MB)).isEqualTo(60);
    }

    @Test
    @DisplayName("超阈值按 MB 线性增长（契约值 51/100/165/441MB）")
    void largeFiles_scaleLinearly() {
        assertThat(SidecarTimeoutBudget.secondsFor(60, 51 * MB)).isEqualTo(303);
        assertThat(SidecarTimeoutBudget.secondsFor(60, 100 * MB)).isEqualTo(450);
        assertThat(SidecarTimeoutBudget.secondsFor(60, 165 * MB)).isEqualTo(645);
        assertThat(SidecarTimeoutBudget.secondsFor(60, 441 * MB)).isEqualTo(1473);
    }

    @Test
    @DisplayName("封顶 1800s（565MB/2GB）")
    void hugeFiles_capAtMax() {
        assertThat(SidecarTimeoutBudget.secondsFor(60, 565 * MB)).isEqualTo(1800);
        assertThat(SidecarTimeoutBudget.secondsFor(60, 2048 * MB)).isEqualTo(1800);
    }

    @Test
    @DisplayName("未知大小（0/负数）走大文件档下限，不回落基准档")
    void unknownSize_usesLargeFloor() {
        assertThat(SidecarTimeoutBudget.secondsFor(60, 0)).isEqualTo(300);
        assertThat(SidecarTimeoutBudget.secondsFor(60, -1)).isEqualTo(300);
    }

    @Test
    @DisplayName("基准档可配置：非法基准回退 60s，自定义基准等比缩放")
    void configurableBase() {
        assertThat(SidecarTimeoutBudget.secondsFor(0, 10 * MB)).isEqualTo(60);
        assertThat(SidecarTimeoutBudget.secondsFor(-5, 10 * MB)).isEqualTo(60);
        assertThat(SidecarTimeoutBudget.secondsFor(120, 100 * MB)).isEqualTo(750);
        assertThat(SidecarTimeoutBudget.secondsFor(120, 0)).isEqualTo(600);
    }

    @Test
    @DisplayName("单调不减：大小增大上限不下降")
    void monotonic() {
        long previous = 0;
        for (long mb = 1; mb <= 2048; mb += 7) {
            long current = SidecarTimeoutBudget.secondsFor(60, mb * MB);
            assertThat(current).isGreaterThanOrEqualTo(previous);
            previous = current;
        }
    }
}
