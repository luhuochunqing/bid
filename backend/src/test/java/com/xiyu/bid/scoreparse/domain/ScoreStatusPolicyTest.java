package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 满足状态判定策略测试（spec 041 FR-015）。
 * <p>满分 = OK；零分 = DANGER；部分得分或证书过期 = PENDING；主观项 = PENDING。
 */
class ScoreStatusPolicyTest {

    private final ScoreStatusPolicy policy = new ScoreStatusPolicy();
    private final BigDecimal weight = new BigDecimal("8");

    @Test
    @DisplayName("客观项满分 → OK（US3 场景 1）")
    void fullScore_returnsOk() {
        assertThat(policy.evaluate(new BigDecimal("8"), weight, "OBJECTIVE", false)).isEqualTo("OK");
    }

    @Test
    @DisplayName("客观项零分 → DANGER（US3 场景 5：知识库无数据）")
    void zeroScore_returnsDanger() {
        assertThat(policy.evaluate(BigDecimal.ZERO, weight, "OBJECTIVE", false)).isEqualTo("DANGER");
    }

    @Test
    @DisplayName("客观项部分得分 → PENDING（US3 场景 2）")
    void partialScore_returnsPending() {
        assertThat(policy.evaluate(new BigDecimal("4"), weight, "OBJECTIVE", false)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("主观项 → PENDING 不看得分（US3 场景 4）")
    void subjective_returnsPending() {
        assertThat(policy.evaluate(null, weight, "SUBJECTIVE", false)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("得分为 null → PENDING")
    void nullScore_returnsPending() {
        assertThat(policy.evaluate(null, weight, "OBJECTIVE", false)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("证书过期标记 → PENDING 即使满分（Edge Cases：过期命中待确认）")
    void certExpired_returnsPending_evenFullScore() {
        assertThat(policy.evaluate(new BigDecimal("8"), weight, "OBJECTIVE", true)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("证书过期标记 + 零分 → PENDING（过期算命中，不算不满足）")
    void certExpired_returnsPending_zeroScore() {
        assertThat(policy.evaluate(BigDecimal.ZERO, weight, "OBJECTIVE", true)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("主观项即使带过期标记也 PENDING")
    void subjective_withFlag_returnsPending() {
        assertThat(policy.evaluate(null, weight, "SUBJECTIVE", true)).isEqualTo("PENDING");
    }
}
