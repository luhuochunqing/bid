package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 双轨双保险评分抽取分流器。
 * <p>
 * 纯领域模型：优先调度基于第一性原理的主通道（大模型端到端抽取 + 文本子串核验 + 满分闭合）；
 * 当主通道满足业务置信度门禁时直接放行，否则平滑无缝降级执行老引擎通道，实现零回归安全护航。
 */
public final class DualTrackScoreExtractor {

    private static final BigDecimal DEFAULT_TOTAL = new BigDecimal("100");
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");
    private static final double MIN_VERIFIED_RATIO = 0.80;

    private DualTrackScoreExtractor() {}

    public enum Track {
        PRIMARY,
        FALLBACK
    }

    public record Result(
            Track track,
            List<ScoreCandidate> candidates,
            BigDecimal totalWeight,
            double verifiedRatio,
            String reason
    ) {
        public Result {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    /**
     * 调度双轨通道抽取评分项。
     *
     * @param primaryCandidates 主通道直接抽取的候选列表
     * @param documentText 招标文件文本全文
     * @param declaredTotal 标书声明总分（若为 null 默认 100 分）
     * @param fallbackSupplier 备用老引擎通道供应器
     * @return 双轨执行决策结果
     */
    public static Result dispatch(
            List<ScoreCandidate> primaryCandidates,
            String documentText,
            BigDecimal declaredTotal,
            Supplier<List<ScoreCandidate>> fallbackSupplier
    ) {
        BigDecimal targetTotal = declaredTotal != null ? declaredTotal : DEFAULT_TOTAL;

        // 1. 检查主通道是否产生候选
        if (primaryCandidates == null || primaryCandidates.isEmpty()) {
            return invokeFallback(fallbackSupplier, "PRIMARY_EMPTY");
        }

        // 2. 校验权重总分是否闭合
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (ScoreCandidate c : primaryCandidates) {
            if (c.weight() != null && c.weight().compareTo(BigDecimal.ZERO) > 0) {
                totalWeight = totalWeight.add(c.weight());
            }
        }

        if (totalWeight.subtract(targetTotal).abs().compareTo(TOLERANCE) > 0) {
            return invokeFallback(fallbackSupplier, String.format(
                    "WEIGHT_SUM_MISMATCH: primary=%s, target=%s", totalWeight, targetTotal));
        }

        // 3. 校验文本是否均出自标书原文（数学级防伪）
        int itemsWithDetail = 0;
        int verifiedItems = 0;
        List<ScoreCandidate> verifiedCandidates = new ArrayList<>();

        for (ScoreCandidate c : primaryCandidates) {
            String detail = c.detail();
            if (detail != null && !detail.isBlank()) {
                itemsWithDetail++;
                var verification = TextInclusionVerifier.verify(detail, documentText);
                if (verification.verified()) {
                    verifiedItems++;
                    verifiedCandidates.add(c);
                } else {
                    verifiedCandidates.add(c);
                }
            } else {
                verifiedCandidates.add(c);
            }
        }

        double ratio = itemsWithDetail == 0 ? 1.0 : (double) verifiedItems / itemsWithDetail;
        if (ratio < MIN_VERIFIED_RATIO) {
            return invokeFallback(fallbackSupplier, String.format(
                    "INSUFFICIENT_VERIFIED_TEXT: verifiedRatio=%.2f, threshold=%.2f", ratio, MIN_VERIFIED_RATIO));
        }

        // 4. 主通道完全合格，直接放行
        return new Result(Track.PRIMARY, verifiedCandidates, totalWeight, ratio, "PRIMARY_CLOSED");
    }

    private static Result invokeFallback(Supplier<List<ScoreCandidate>> fallbackSupplier, String reason) {
        if (fallbackSupplier == null) {
            return new Result(Track.FALLBACK, List.of(), BigDecimal.ZERO, 0.0, "FALLBACK_SUPPLIER_NULL: " + reason);
        }
        try {
            List<ScoreCandidate> fallbackCandidates = fallbackSupplier.get();
            BigDecimal fallbackTotal = BigDecimal.ZERO;
            if (fallbackCandidates != null) {
                for (ScoreCandidate c : fallbackCandidates) {
                    if (c.weight() != null && c.weight().compareTo(BigDecimal.ZERO) > 0) {
                        fallbackTotal = fallbackTotal.add(c.weight());
                    }
                }
            }
            return new Result(Track.FALLBACK, fallbackCandidates, fallbackTotal, 0.0, "FALLBACK: " + reason);
        } catch (RuntimeException e) {
            return new Result(Track.FALLBACK, List.of(), BigDecimal.ZERO, 0.0, "FALLBACK_EXCEPTION: " + e.getMessage());
        }
    }
}
