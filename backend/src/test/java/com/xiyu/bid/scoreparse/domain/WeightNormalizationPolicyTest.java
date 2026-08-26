// Input: WeightNormalizationPolicy（normalize 方法）
// Output: 权重合计缺口/超载归一化 + 确定性/相对比例保持验证（spec 047 US1/US2）
// Pos: Test/scoreparse/domain — 纯核心单测，无 Spring 上下文

package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeightNormalizationPolicyTest {

    private final WeightNormalizationPolicy policy = new WeightNormalizationPolicy();

    @Test
    void normalize_gapTotal90_normalizedTo100WithOriginal() {
        // US1 场景1：合计 90（缺 10 分）→ 归一后合计=100，originalTotal=90
        WeightNormalizationPolicy.Result result = policy.normalize(
                List.of(candidate("1", "10"), candidate("2", "20"), candidate("3", "60")));

        assertThat(result.originalTotal()).isEqualByComparingTo("90");
        assertThat(sumWeights(result.normalizedCandidates())).isEqualByComparingTo("100");
    }

    @Test
    void normalize_overloadTotal200_normalizedTo100() {
        // US1 场景2：超载 200 → 归一收敛到 100
        WeightNormalizationPolicy.Result result = policy.normalize(
                List.of(candidate("1", "50"), candidate("2", "150")));

        assertThat(result.originalTotal()).isEqualByComparingTo("200");
        assertThat(sumWeights(result.normalizedCandidates())).isEqualByComparingTo("100");
    }

    @Test
    void normalize_exactly100_passthroughNullOriginal() {
        // US1 场景3：恰好 100 → 原样透传，originalTotal=null
        WeightNormalizationPolicy.Result result = policy.normalize(
                List.of(candidate("1", "10"), candidate("2", "20"), candidate("3", "70")));

        assertThat(result.originalTotal()).isNull();
        assertThat(sumWeights(result.normalizedCandidates())).isEqualByComparingTo("100");
    }

    @Test
    void normalize_withinTolerance_passthroughNullOriginal() {
        // 合计在容差 ±0.5 内（99.6）→ 不归一，原样透传
        WeightNormalizationPolicy.Result result = policy.normalize(
                List.of(candidate("1", "70"), candidate("2", "20"), candidate("3", "9.6")));

        assertThat(result.originalTotal()).isNull();
        List<BigDecimal> weights = result.normalizedCandidates().stream()
                .map(ScoreCandidate::weight).toList();
        assertThat(weights.size()).isEqualTo(3);
        assertThat(weights.get(0)).isEqualByComparingTo(new BigDecimal("70"));
        assertThat(weights.get(1)).isEqualByComparingTo(new BigDecimal("20"));
        assertThat(weights.get(2)).isEqualByComparingTo(new BigDecimal("9.6"));
    }

    @Test
    void normalize_emptyCandidates_passthroughNullOriginal() {
        // total=0（空候选集）→ 不归一，原样空列表 + null（保留 0 项失败兜底）
        WeightNormalizationPolicy.Result result = policy.normalize(List.of());

        assertThat(result.originalTotal()).isNull();
        assertThat(result.normalizedCandidates()).isEmpty();
    }

    @Test
    void normalize_relativeRatioPreserved() {
        // US2：归一化保持相对权重比例（20:40:20:10 = 2:4:2:1）
        WeightNormalizationPolicy.Result result = policy.normalize(
                List.of(candidate("1", "20"), candidate("2", "40"),
                        candidate("3", "20"), candidate("4", "10")));

        List<BigDecimal> weights = result.normalizedCandidates().stream()
                .map(ScoreCandidate::weight).toList();
        // 与原 2:1 比例一致（项1:项2）≈ 2，项1:项4 ≈ 2，项2:项4 ≈ 4
        assertThat(ratio(weights.get(1), weights.get(0))).isCloseTo(new BigDecimal("2"), within("0.05"));
        assertThat(ratio(weights.get(0), weights.get(3))).isCloseTo(new BigDecimal("2"), within("0.05"));
        assertThat(ratio(weights.get(1), weights.get(3))).isCloseTo(new BigDecimal("4"), within("0.1"));
    }

    @Test
    void normalize_deterministicReproducible() {
        // US2：同一输入两次归一 → 输出逐位一致
        List<ScoreCandidate> input = List.of(candidate("1", "10"), candidate("2", "20"), candidate("3", "60"));

        WeightNormalizationPolicy.Result first = policy.normalize(input);
        WeightNormalizationPolicy.Result second = policy.normalize(input);

        assertThat(first.normalizedCandidates()).containsExactlyElementsOf(second.normalizedCandidates());
        assertThat(first.originalTotal()).isEqualByComparingTo(second.originalTotal());
    }

    @Test
    void normalize_orderIndependent_totalStillConverges() {
        // US2：相同候选不同顺序，剩余额度分配正确性保持一致（各合计仍=100）
        var inputA = List.of(candidate("1", "10"), candidate("2", "20"), candidate("3", "60"));
        var inputB = List.of(candidate("2", "20"), candidate("3", "60"), candidate("1", "10"));

        assertThat(sumWeights(policy.normalize(inputA).normalizedCandidates()))
                .isEqualByComparingTo("100");
        assertThat(sumWeights(policy.normalize(inputB).normalizedCandidates()))
                .isEqualByComparingTo("100");
    }

    @Test
    void normalize_onlyWeightModified_otherFieldsPreserved() {
        // 归一化仅改写 weight，其余候选字段（code/dim/detail/sourceText…）保持不变
        ScoreCandidate original = new ScoreCandidate("A1", "资质证明", "条目内容",
                new BigDecimal("30"), "OBJECTIVE", "注", "条款原文", "P9", "pattern:x");
        WeightNormalizationPolicy.Result result = policy.normalize(List.of(original));

        ScoreCandidate normalized = result.normalizedCandidates().get(0);
        assertThat(normalized.code()).isEqualTo("A1");
        assertThat(normalized.dim()).isEqualTo("资质证明");
        assertThat(normalized.detail()).isEqualTo("条目内容");
        assertThat(normalized.scoreTypeGuess()).isEqualTo("OBJECTIVE");
        assertThat(normalized.contextNote()).isEqualTo("注");
        assertThat(normalized.sourceText()).isEqualTo("条款原文");
        assertThat(normalized.location()).isEqualTo("P9");
        assertThat(normalized.semanticPattern()).isEqualTo("pattern:x");
        // 单独项归一化：total=30，scale=100/30，weight 从 30 变为 100（也证明只改 weight 的类型位置）
        // 此处仅验证字段完整保留 + weight 与原始不同（归一化生效）
        assertThat(normalized.weight()).isNotEqualByComparingTo("30");
    }

    @Test
    void normalize_nullWeightMember_nullStaysNull() {
        // 健壮性：候选含 null 权重时，null 项保持原样，non-null 项被归一，合计仅计 non-null
        ScoreCandidate nullWeight = new ScoreCandidate(null, "维度", "要素",
                null, null, null, null, null, null);
        WeightNormalizationPolicy.Result result = policy.normalize(List.of(candidate("1", "60"), nullWeight));

        assertThat(result.originalTotal()).isNotEqualTo(BigDecimal.ZERO);
        assertThat(result.normalizedCandidates().get(1).weight()).isNull();
        assertThat(sumWeights(result.normalizedCandidates())).isEqualByComparingTo("100");
    }

    @Test
    void normalize_extremeTotalOver1000_passthroughNullOriginal() {
        // 防御：合计超过 1000 分（异常年份/金额污染）→ 拒绝归一化，原样透传，originalTotal=null
        WeightNormalizationPolicy.Result result = policy.normalize(
                List.of(candidate("1", "2024"), candidate("2", "50000000")));

        assertThat(result.originalTotal()).isNull();
        assertThat(result.normalizedCandidates().get(0).weight()).isEqualByComparingTo("2024");
        assertThat(result.normalizedCandidates().get(1).weight()).isEqualByComparingTo("50000000");
    }

    private BigDecimal ratio(BigDecimal a, BigDecimal b) {
        return a.divide(b, 4, java.math.RoundingMode.HALF_UP);
    }

    private org.assertj.core.data.Offset<BigDecimal> within(String offset) {
        return org.assertj.core.data.Offset.offset(new BigDecimal(offset));
    }

    private BigDecimal sumWeights(List<ScoreCandidate> candidates) {
        BigDecimal sum = BigDecimal.ZERO;
        for (ScoreCandidate candidate : candidates) {
            if (candidate.weight() != null) {
                sum = sum.add(candidate.weight());
            }
        }
        return sum;
    }

    private ScoreCandidate candidate(String code, String weight) {
        return new ScoreCandidate(code, "维度", "要素", new BigDecimal(weight), null, null,
                "条款原文", "P9", null);
    }
}