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
}
