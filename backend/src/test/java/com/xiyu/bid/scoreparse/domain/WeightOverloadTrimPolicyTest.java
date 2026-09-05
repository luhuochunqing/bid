// Input: WeightOverloadTrimPolicy（trim 方法）
// Output: 权重超载两轮剔除行为验证（spec 046 FR-002 / FR-006）
// Pos: Test/scoreparse/domain

package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeightOverloadTrimPolicyTest {

    private static final BigDecimal LIMIT = new BigDecimal("100.5");

    private final WeightOverloadTrimPolicy policy = new WeightOverloadTrimPolicy();

    @Test
    void trim_ghostNoCodeOverload_removedAndStopsAtLimit() {
        // project 212 场景：真实 100 + 幽灵 100（无 code）= 200 → 剔幽灵后 100 即停
        ScoreCandidate real1 = candidate("1", "60", true);
        ScoreCandidate real2 = candidate("2", "40", true);
        ScoreCandidate ghost = candidate(null, "100", true);

        WeightOverloadTrimPolicy.TrimResult result =
                policy.trim(List.of(real1, real2, ghost), LIMIT);

        assertThat(result.remaining()).containsExactly(real1, real2);
        assertThat(result.removed()).containsExactly(ghost);
    }

    @Test
    void trim_noCodeExhausted_continuesToNoSourceText() {
        // 无 code 候选剔尽仍超 → 第二轮继续剔无 sourceText 候选
        ScoreCandidate real = candidate("1", "90", true);
        ScoreCandidate ghost = candidate(null, "5", true);
        ScoreCandidate noSource = candidate("2", "30", false);
        // total 125 > 100.5：剔 ghost → 120 仍超；第二轮剔 noSource → 90 达标

        WeightOverloadTrimPolicy.TrimResult result =
                policy.trim(List.of(real, ghost, noSource), LIMIT);

        assertThat(result.remaining()).containsExactly(real);
        assertThat(result.removed()).containsExactly(ghost, noSource);
    }

    @Test
    void trim_allRoundsExhaustedStillOver_keepsRemaining() {
        // 两轮剔尽仍超：不回滚，remaining 保留真实项，removed 记录全部被剔
        ScoreCandidate real1 = candidate("1", "60", true);
        ScoreCandidate real2 = candidate("2", "60", true);
        ScoreCandidate ghost = candidate(null, "5", true);
        // total 125：剔 ghost → 120 仍超；无可剔嫌疑 → 停止，weightWarning 兜底

        WeightOverloadTrimPolicy.TrimResult result =
                policy.trim(List.of(real1, real2, ghost), LIMIT);

        assertThat(result.remaining()).containsExactly(real1, real2);
        assertThat(result.removed()).containsExactly(ghost);
    }

    @Test
    void trim_allCandidatesTrimmed_remainingMayBeEmpty() {
        // 极端：唯一候选无 code 且自身超载 → 剔除后 remaining 为空
        ScoreCandidate solo = candidate(null, "120", true);

        WeightOverloadTrimPolicy.TrimResult result = policy.trim(List.of(solo), LIMIT);

        assertThat(result.remaining()).isEmpty();
        assertThat(result.removed()).containsExactly(solo);
    }

    @Test
    void trim_realItemsNeverRemoved() {
        // 有 code 且有 sourceText 的真实项永不出现在 removed
        ScoreCandidate real = candidate("1", "60", true);
        ScoreCandidate noSource = candidate("2", "60", false);
        // total 120：第一轮无可剔（均有 code）→ 第二轮剔 noSource → 60 达标

        WeightOverloadTrimPolicy.TrimResult result =
                policy.trim(List.of(real, noSource), LIMIT);

        assertThat(result.removed()).containsExactly(noSource);
        assertThat(result.remaining()).containsExactly(real);
    }

    @Test
    void trim_totalWithinLimit_noOp() {
        ScoreCandidate c1 = candidate("1", "60", true);
        ScoreCandidate c2 = candidate("2", "40", true);

        WeightOverloadTrimPolicy.TrimResult result = policy.trim(List.of(c1, c2), LIMIT);

        assertThat(result.remaining()).containsExactly(c1, c2);
        assertThat(result.removed()).isEmpty();
    }

    @Test
    void trim_tableCandidateWithoutCodeOverload_notExemptedAndTrimmed() {
        // 表格候选（STRUCTURED_TABLE）如果缺少 code 且导致超载（如父汇总项），不可被盲目豁免，必须在第一轮被剔除
        ScoreCandidate tableParentSummary = new ScoreCandidate(null, "商务部分", "商务评分汇总 50分",
                new BigDecimal("50"), null, null, "原文", "P1", "STRUCTURED_TABLE");
        ScoreCandidate child1 = new ScoreCandidate("1.1", "财务能力", "财务 30分",
                new BigDecimal("30"), null, null, "原文", "P1", "STRUCTURED_TABLE");
        ScoreCandidate child2 = new ScoreCandidate("1.2", "供应商业绩", "业绩 30分",
                new BigDecimal("30"), null, null, "原文", "P1", "STRUCTURED_TABLE");
        ScoreCandidate normalOther = candidate("2", "40", true);
        // total: 50 + 30 + 30 + 40 = 150 > 100.5
        // 第一轮剔除无 code 的 tableParentSummary 50分 -> 剩余 100 <= 100.5 达标即停！
        WeightOverloadTrimPolicy.TrimResult result =
                policy.trim(List.of(tableParentSummary, child1, child2, normalOther), LIMIT);

        assertThat(result.removed()).containsExactly(tableParentSummary);
        assertThat(result.remaining()).containsExactly(child1, child2, normalOther);
    }

    @Test
    void trim_nullWeight_skippedInSumButStillTrimmable() {
        // total = Σ 非 null weight：null 权重不计入合计，但无 code 候选仍可剔
        ScoreCandidate real = candidate("1", "110", true);
        ScoreCandidate nullWeightGhost = new ScoreCandidate(null, "维度", "要素",
                null, null, null, "条款原文", "P9", null);
        // total 110 > 100.5：剔 nullWeightGhost（合计不变仍超）→ 剔尽停止，真实项保留

        WeightOverloadTrimPolicy.TrimResult result =
                policy.trim(List.of(real, nullWeightGhost), LIMIT);

        assertThat(result.remaining()).containsExactly(real);
        assertThat(result.removed()).containsExactly(nullWeightGhost);
    }

    @Test
    void trim_inputNotModified() {
        ScoreCandidate real = candidate("1", "60", true);
        ScoreCandidate ghost = candidate(null, "100", true);
        List<ScoreCandidate> input = new ArrayList<>(List.of(real, ghost));
        List<ScoreCandidate> snapshot = List.copyOf(input);

        policy.trim(input, LIMIT);

        assertThat(input).containsExactlyElementsOf(snapshot);
    }

    private ScoreCandidate candidate(String code, String weight, boolean withSource) {
        return new ScoreCandidate(code, "维度", "要素", new BigDecimal(weight), null, null,
                withSource ? "条款原文" : null, "P9", null);
    }
}
