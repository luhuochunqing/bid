package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class DualTrackScoreExtractorTest {

    private static ScoreCandidate candidate(String code, String dim, BigDecimal weight, String detail) {
        return new ScoreCandidate(code, dim, detail, weight, "OBJECTIVE", null, detail, null, "PRIMARY");
    }

    @Test
    @DisplayName("主通道满足满分闭环且文本全部出自标书时，直接采用主通道结果")
    void primaryTrackAdoptedWhenWeightClosesAndTextVerified() {
        String doc = "综合评分100分。1.财务状况10分：审计报告连续三年无保留意见。2.业绩90分：具备同类项目业绩。";
        var candidate1 = candidate("1", "财务状况", new BigDecimal("10"), "审计报告连续三年无保留意见。");
        var candidate2 = candidate("2", "业绩", new BigDecimal("90"), "具备同类项目业绩。");

        AtomicBoolean fallbackCalled = new AtomicBoolean(false);
        var result = DualTrackScoreExtractor.dispatch(
                List.of(candidate1, candidate2),
                doc,
                new BigDecimal("100"),
                () -> {
                    fallbackCalled.set(true);
                    return List.of();
                }
        );

        assertThat(result.track()).isEqualTo(DualTrackScoreExtractor.Track.PRIMARY);
        assertThat(result.candidates()).hasSize(2);
        assertThat(result.totalWeight()).isEqualByComparingTo("100");
        assertThat(result.verifiedRatio()).isEqualTo(1.0);
        assertThat(fallbackCalled.get()).isFalse();
    }

    @Test
    @DisplayName("主通道候选为空时，平滑降级执行备用老引擎通道")
    void fallbackInvokedWhenPrimaryIsEmpty() {
        String doc = "一些标书内容";
        var legacyCandidate = candidate("1", "老通道项", new BigDecimal("100"), "老通道细则");

        AtomicBoolean fallbackCalled = new AtomicBoolean(false);
        var result = DualTrackScoreExtractor.dispatch(
                List.of(),
                doc,
                new BigDecimal("100"),
                () -> {
                    fallbackCalled.set(true);
                    return List.of(legacyCandidate);
                }
        );

        assertThat(result.track()).isEqualTo(DualTrackScoreExtractor.Track.FALLBACK);
        assertThat(result.candidates()).containsExactly(legacyCandidate);
        assertThat(fallbackCalled.get()).isTrue();
    }

    @Test
    @DisplayName("主通道权重未闭合时（如50分vs100分），平滑降级执行备用老引擎通道")
    void fallbackInvokedWhenPrimaryWeightDoesNotClose() {
        String doc = "标书内容";
        var candidate = candidate("1", "单项", new BigDecimal("50"), "细则");

        AtomicBoolean fallbackCalled = new AtomicBoolean(false);
        var result = DualTrackScoreExtractor.dispatch(
                List.of(candidate),
                doc,
                new BigDecimal("100"),
                () -> {
                    fallbackCalled.set(true);
                    return List.of();
                }
        );

        assertThat(result.track()).isEqualTo(DualTrackScoreExtractor.Track.FALLBACK);
        assertThat(fallbackCalled.get()).isTrue();
        assertThat(result.reason()).contains("WEIGHT_SUM_MISMATCH");
    }

    @Test
    @DisplayName("树形结构：父项与子项共存时，自动感知树形分离并杜绝双计，末级叶子项闭合时放行")
    void treeStructureParentAndLeafRecognizedAndClosed() {
        String doc = "综合评分100分。一、商务部分30分。1.1财务指标10分：审计报告合格。1.2企业业绩20分：同类业绩合同。二、技术部分70分。2.1技术方案40分：方案详尽可行。2.2售后服务30分：响应时间小于2小时。";

        // 父项 1：商务部分 30分
        var parent1 = new ScoreCandidate("一", "商务部分", "一、商务部分30分", new BigDecimal("30"), "OBJECTIVE", null, "一、商务部分30分", null, "PRIMARY", null, "UNKNOWN");
        // 子项 1.1 与 1.2
        var leaf11 = new ScoreCandidate("1.1", "财务指标", "审计报告合格。", new BigDecimal("10"), "OBJECTIVE", null, "审计报告合格。", null, "PRIMARY", "商务部分", "CELL");
        var leaf12 = new ScoreCandidate("1.2", "企业业绩", "同类业绩合同。", new BigDecimal("20"), "OBJECTIVE", null, "同类业绩合同。", null, "PRIMARY", "商务部分", "CELL");

        // 父项 2：技术部分 70分
        var parent2 = new ScoreCandidate("二", "技术部分", "二、技术部分70分", new BigDecimal("70"), "OBJECTIVE", null, "二、技术部分70分", null, "PRIMARY", null, "UNKNOWN");
        // 子项 2.1 与 2.2
        var leaf21 = new ScoreCandidate("2.1", "技术方案", "方案详尽可行。", new BigDecimal("40"), "SUBJECTIVE", null, "方案详尽可行。", null, "PRIMARY", "技术部分", "CELL");
        var leaf22 = new ScoreCandidate("2.2", "售后服务", "响应时间小于2小时。", new BigDecimal("30"), "OBJECTIVE", null, "响应时间小于2小时。", null, "PRIMARY", "技术部分", "CELL");

        // 平铺时总分为 30 + 10 + 20 + 70 + 40 + 30 = 200分！
        List<ScoreCandidate> all = List.of(parent1, leaf11, leaf12, parent2, leaf21, leaf22);

        AtomicBoolean fallbackCalled = new AtomicBoolean(false);
        var result = DualTrackScoreExtractor.dispatch(
                all,
                doc,
                new BigDecimal("100"),
                () -> {
                    fallbackCalled.set(true);
                    return List.of();
                }
        );

        assertThat(result.track()).isEqualTo(DualTrackScoreExtractor.Track.PRIMARY);
        assertThat(fallbackCalled.get()).isFalse();
        // 核心断言：杜绝 200 分双计，总分正确识别为 100 分
        assertThat(result.totalWeight()).isEqualByComparingTo("100");
        // 核心断言：放行候选集仅包含 4 个末级叶子项，父级大类作为元数据分离
        assertThat(result.candidates()).containsExactlyInAnyOrder(leaf11, leaf12, leaf21, leaf22);
        assertThat(result.parentCandidates()).containsExactlyInAnyOrder(parent1, parent2);
    }

    @Test
    @DisplayName("树形结构：父项声明分值与直接子项合计不自洽时，触发降级")
    void parentChildInconsistentFallsBack() {
        String doc = "商务标声明30分，但子项细则加和只有25分。";
        var parent = new ScoreCandidate("一", "商务标", "商务标30分", new BigDecimal("30"), "OBJECTIVE", null, "商务标30分", null, "PRIMARY", null, "UNKNOWN");
        var leaf1 = new ScoreCandidate("1.1", "资质", "相关资质", new BigDecimal("10"), "OBJECTIVE", null, "相关资质", null, "PRIMARY", "商务标", "CELL");
        var leaf2 = new ScoreCandidate("1.2", "业绩", "相关业绩", new BigDecimal("15"), "OBJECTIVE", null, "相关业绩", null, "PRIMARY", "商务标", "CELL");

        AtomicBoolean fallbackCalled = new AtomicBoolean(false);
        var result = DualTrackScoreExtractor.dispatch(
                List.of(parent, leaf1, leaf2),
                doc,
                new BigDecimal("100"),
                () -> {
                    fallbackCalled.set(true);
                    return List.of();
                }
        );

        assertThat(result.track()).isEqualTo(DualTrackScoreExtractor.Track.FALLBACK);
        assertThat(fallbackCalled.get()).isTrue();
        assertThat(result.reason()).contains("PARENT_CHILD_INCONSISTENT");
    }

    @Test
    @DisplayName("编号前缀层级推导：未显式指定 parentDim 时，也能通过分词层级推导出父子关系")
    void hierarchicalCodePrefixRecognizedWithoutExplicitParentDim() {
        String doc = "1.技术指标50分。1.1系统功能30分：系统功能完备。1.2系统性能20分：响应性能优异。2.商务50分：企业财务健全。";
        // 父项：编号为 1，无独立细则
        var parent1 = new ScoreCandidate("1", "技术指标", "", new BigDecimal("50"), "OBJECTIVE", null, "", null, "PRIMARY", null, "UNKNOWN");
        var leaf11 = new ScoreCandidate("1.1", "系统功能", "系统功能完备。", new BigDecimal("30"), "OBJECTIVE", null, "系统功能完备。", null, "PRIMARY", null, "UNKNOWN");
        var leaf12 = new ScoreCandidate("1.2", "系统性能", "响应性能优异。", new BigDecimal("20"), "OBJECTIVE", null, "响应性能优异。", null, "PRIMARY", null, "UNKNOWN");
        var leaf2 = new ScoreCandidate("2", "商务", "企业财务健全。", new BigDecimal("50"), "OBJECTIVE", null, "企业财务健全。", null, "PRIMARY", null, "UNKNOWN");

        var result = DualTrackScoreExtractor.dispatch(
                List.of(parent1, leaf11, leaf12, leaf2),
                doc,
                new BigDecimal("100"),
                () -> List.of()
        );

        assertThat(result.track()).isEqualTo(DualTrackScoreExtractor.Track.PRIMARY);
        assertThat(result.totalWeight()).isEqualByComparingTo("100");
        assertThat(result.candidates()).containsExactlyInAnyOrder(leaf11, leaf12, leaf2);
        assertThat(result.parentCandidates()).containsExactly(parent1);
    }

    @Test
    @DisplayName("主通道细则全部为幻觉未在原文命中时，平滑降级执行备用老引擎通道")
    void fallbackInvokedWhenPrimaryVerificationRateTooLow() {
        String doc = "真实的标书只有技术规范，没有任何商务评分";
        var candidate1 = candidate("1", "虚构项1", new BigDecimal("50"), "完全捏造的虚假细则A");
        var candidate2 = candidate("2", "虚构项2", new BigDecimal("50"), "完全捏造的虚假细则B");

        AtomicBoolean fallbackCalled = new AtomicBoolean(false);
        var result = DualTrackScoreExtractor.dispatch(
                List.of(candidate1, candidate2),
                doc,
                new BigDecimal("100"),
                () -> {
                    fallbackCalled.set(true);
                    return List.of();
                }
        );

        assertThat(result.track()).isEqualTo(DualTrackScoreExtractor.Track.FALLBACK);
        assertThat(fallbackCalled.get()).isTrue();
        assertThat(result.reason()).contains("INSUFFICIENT_VERIFIED_TEXT");
    }

    @Test
    @DisplayName("PARENT_SINGLE: isParentOnlyDim 为 true 的大类项不计入叶子权重总分")
    void isParentOnlyDimCandidateExcludedFromLeafTotal() {
        String doc = "技术部分80分。1.方案50分：满足要求。2.团队30分：经验丰富。商务部分20分：无扣分。";
        var parentSingle = new ScoreCandidate("一", "技术部分", "技术部分80分", new BigDecimal("80"), "OBJECTIVE", null, "技术部分80分", null, "PRIMARY", null, "PARENT_SINGLE");
        var leaf1 = new ScoreCandidate("1", "方案", "满足要求。", new BigDecimal("50"), "OBJECTIVE", null, "满足要求。", null, "PRIMARY", "技术部分", "CELL");
        var leaf2 = new ScoreCandidate("2", "团队", "经验丰富。", new BigDecimal("30"), "OBJECTIVE", null, "经验丰富。", null, "PRIMARY", "技术部分", "CELL");
        var leaf3 = new ScoreCandidate("3", "商务部分", "无扣分。", new BigDecimal("20"), "OBJECTIVE", null, "无扣分。", null, "PRIMARY", null, "CELL");

        var result = DualTrackScoreExtractor.dispatch(
                List.of(parentSingle, leaf1, leaf2, leaf3),
                doc,
                new BigDecimal("100"),
                () -> List.of()
        );

        assertThat(result.track()).isEqualTo(DualTrackScoreExtractor.Track.PRIMARY);
        assertThat(result.totalWeight()).isEqualByComparingTo("100");
        assertThat(result.candidates()).containsExactlyInAnyOrder(leaf1, leaf2, leaf3);
        assertThat(result.parentCandidates()).containsExactly(parentSingle);
    }
    @Test
    void blankRulesCannotQualifyAsVerifiedPrimary() {
        var result = DualTrackScoreExtractor.dispatch(
                List.of(candidate("1", "空规则", new BigDecimal("100"), "")),
                "评分总分100分", new BigDecimal("100"), List::of);
        assertThat(result.track()).isEqualTo(DualTrackScoreExtractor.Track.FALLBACK);
    }

    @Test
    void eightyPercentVerifiedCannotPublishUnverifiedRules() {
        var facts = java.util.stream.IntStream.range(0, 5)
                .mapToObj(i -> candidate("" + i, "项" + i, new BigDecimal("20"), "规则" + i)).toList();
        var result = DualTrackScoreExtractor.dispatch(facts, "规则0规则1规则2规则3",
                new BigDecimal("100"), List::of);
        assertThat(result.track()).isEqualTo(DualTrackScoreExtractor.Track.FALLBACK);
    }
}

