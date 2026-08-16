// Input: WeightSumCheck（check 方法）
// Output: 权重合计闭环校验行为验证（spec 041 FR-005 / FR-022）
// Pos: Test/scoreparse/domain

package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeightSumCheckTest {

    private final WeightSumCheck check = new WeightSumCheck();

    @Test
    void check_sumEquals100_noWarning() {
        WeightSumCheck.Result result = check.check(List.of(
                new BigDecimal("60"), new BigDecimal("40")
        ));
        assertThat(result.totalWeight()).isEqualByComparingTo("100");
        assertThat(result.weightWarning()).isFalse();
        assertThat(result.needRecheck()).isFalse();
    }

    @Test
    void check_sumNot100_weightWarningAndRecheck() {
        // 合计≠100：标记 weightWarning（FR-022）+ 触发二次解析/回补标记（FR-005）
        WeightSumCheck.Result result = check.check(List.of(
                new BigDecimal("60"), new BigDecimal("30")
        ));
        assertThat(result.totalWeight()).isEqualByComparingTo("90");
        assertThat(result.weightWarning()).isTrue();
        assertThat(result.needRecheck()).isTrue();
    }

    @Test
    void check_sumExceeds100_warning() {
        WeightSumCheck.Result result = check.check(List.of(
                new BigDecimal("70"), new BigDecimal("50")
        ));
        assertThat(result.totalWeight()).isEqualByComparingTo("120");
        assertThat(result.weightWarning()).isTrue();
    }

    @Test
    void check_nullWeights_ignoredInSum() {
        // 缺失权重的候选由 Validation 层丢弃；此处 null 不参与合计
        WeightSumCheck.Result result = check.check(java.util.Arrays.asList(
                new BigDecimal("60"), null, new BigDecimal("40")
        ));
        assertThat(result.totalWeight()).isEqualByComparingTo("100");
        assertThat(result.weightWarning()).isFalse();
    }

    @Test
    void check_emptyList_zeroSum_warning() {
        WeightSumCheck.Result result = check.check(List.of());
        assertThat(result.totalWeight()).isEqualByComparingTo("0");
        assertThat(result.weightWarning()).isTrue();
    }

    @Test
    void check_tolerancePointFive_noWarning() {
        // 容差 ±0.5 分：四舍五入误差不告警
        WeightSumCheck.Result result = check.check(List.of(
                new BigDecimal("33.3"), new BigDecimal("33.3"), new BigDecimal("33.4")
        ));
        assertThat(result.weightWarning()).isFalse();
    }

    @Test
    void checkCandidates_declaredDimensionMatched_noRecheck() {
        ScoreCandidate c1 = new ScoreCandidate("A1", "技术方案（30分）", "架构设计", new BigDecimal("15"), "SUBJECTIVE", null, null, null, null);
        ScoreCandidate c2 = new ScoreCandidate("A2", "技术方案（30分）", "实施方案", new BigDecimal("15"), "SUBJECTIVE", null, null, null, null);
        ScoreCandidate c3 = new ScoreCandidate("B1", "商务部分（70分）", "报价", new BigDecimal("70"), "SUBJECTIVE", null, null, null, null);

        WeightSumCheck.Result result = check.checkCandidates(List.of(c1, c2, c3));
        assertThat(result.totalWeight()).isEqualByComparingTo("100");
        assertThat(result.weightWarning()).isFalse();
        assertThat(result.needRecheck()).isFalse();
        assertThat(result.dimensionWeights()).containsEntry("技术方案（30分）", new BigDecimal("30"));
    }

    @Test
    void checkCandidates_ruleSentenceNotMistakenAsDeclaredWeight() {
        // 规则细则「每提供一个得2分，最高10分」不应被误判为维度声明2分
        ScoreCandidate c1 = new ScoreCandidate("C1", "资质业绩", "每提供一个得2分，最高10分", new BigDecimal("10"), "OBJECTIVE", "每提供一个得2分", null, null, null);
        ScoreCandidate c2 = new ScoreCandidate("C2", "技术方案", "方案描述", new BigDecimal("90"), "SUBJECTIVE", null, null, null, null);

        WeightSumCheck.Result result = check.checkCandidates(List.of(c1, c2));
        assertThat(result.totalWeight()).isEqualByComparingTo("100");
        assertThat(result.weightWarning()).isFalse();
        assertThat(result.needRecheck()).isFalse();
    }

    @Test
    void checkCandidates_declaredDimensionMismatch_triggersRecheck() {
        // 维度声明 30 分，但项合计只有 20 分 -> 差值超 ±0.5 触发回补
        ScoreCandidate c1 = new ScoreCandidate("A1", "技术方案（30分）", "架构设计", new BigDecimal("20"), "SUBJECTIVE", null, null, null, null);
        ScoreCandidate c2 = new ScoreCandidate("B1", "商务部分（70分）", "报价", new BigDecimal("80"), "SUBJECTIVE", null, null, null, null);

        WeightSumCheck.Result result = check.checkCandidates(List.of(c1, c2));
        assertThat(result.needRecheck()).isTrue();
    }

    @Test
    void checkCandidates_dimWithoutScore_contextHeaderMatched() {
        // dim 仅为「技术方案」，但 contextNote 为「# 第三章 评标办法 - 技术部分（30分）」
        ScoreCandidate c1 = new ScoreCandidate("A1", "技术方案", "架构设计", new BigDecimal("15"), "SUBJECTIVE", "# 第三章 评标办法 - 技术部分（30分）", null, null, null);
        ScoreCandidate c2 = new ScoreCandidate("A2", "技术方案", "实施方案", new BigDecimal("15"), "SUBJECTIVE", "# 第三章 评标办法 - 技术部分（30分）", null, null, null);
        ScoreCandidate c3 = new ScoreCandidate("B1", "商务部分", "报价", new BigDecimal("70"), "SUBJECTIVE", "【商务部分：70分】", null, null, null);

        WeightSumCheck.Result result = check.checkCandidates(List.of(c1, c2, c3));
        assertThat(result.totalWeight()).isEqualByComparingTo("100");
        assertThat(result.weightWarning()).isFalse();
        assertThat(result.needRecheck()).isFalse();
    }
}
