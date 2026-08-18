// Input: 权重合计超载的候选池 + 合计上限
// Output: 两轮嫌疑剔除后的剩余集与被剔集（TrimResult）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 046 FR-002 / FR-006
package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 权重超载剔除策略（spec 046 FR-002）。
 * <p>触发条件由调用方判定（weightSumCheck total &gt; 100.5）。两轮剔除从弱到强：
 * ①无 code ②无 sourceText；每轮按候选序逐个剔、剔一个重算合计，total ≤ limit 即停；
 * 两轮剔尽仍超不回滚（保留现状，weightWarning 语义兜底），真实项零误删。
 */
public class WeightOverloadTrimPolicy {

    /**
     * 剔除结果。
     *
     * @param remaining 剩余候选（保持原相对顺序）
     * @param removed   被剔候选（按剔除顺序，供调用方 WARN 日志）
     */
    public record TrimResult(List<ScoreCandidate> remaining, List<ScoreCandidate> removed) {
    }

    /** 按上限剔除嫌疑候选：无 code → 无 sourceText 两轮逐个剔，达标即停 */
    public TrimResult trim(List<ScoreCandidate> candidates, BigDecimal limit) {
        if (candidates == null || candidates.isEmpty()) {
            return new TrimResult(List.of(), List.of());
        }
        List<ScoreCandidate> remaining = new ArrayList<>(candidates);
        List<ScoreCandidate> removed = new ArrayList<>();
        if (sumWeights(remaining).compareTo(limit) <= 0) {
            return new TrimResult(remaining, removed);
        }
        trimByPredicate(remaining, removed, limit, candidate -> isBlank(candidate.code()));
        trimByPredicate(remaining, removed, limit, candidate -> isBlank(candidate.sourceText()));
        return new TrimResult(remaining, removed);
    }

    private void trimByPredicate(List<ScoreCandidate> remaining, List<ScoreCandidate> removed,
                                 BigDecimal limit, Predicate<ScoreCandidate> suspect) {
        if (sumWeights(remaining).compareTo(limit) <= 0) {
            return;
        }
        for (int i = 0; i < remaining.size(); ) {
            if (suspect.test(remaining.get(i))) {
                removed.add(remaining.remove(i));
                if (sumWeights(remaining).compareTo(limit) <= 0) {
                    return;
                }
            } else {
                i++;
            }
        }
    }

    private BigDecimal sumWeights(List<ScoreCandidate> candidates) {
        BigDecimal total = BigDecimal.ZERO;
        for (ScoreCandidate candidate : candidates) {
            if (candidate.weight() != null) {
                total = total.add(candidate.weight());
            }
        }
        return total;
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
