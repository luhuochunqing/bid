// Input: 去噪候选池 + LLM 全局归并输出 + 评分章节原文
// Output: Outcome（采纳清单 + 幻觉丢弃数 + 回补数）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 055 research R3 双向防御

package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 归并裁决结果守卫（spec 055 US1 边界情况）。
 * <p>规则 1 幻觉过滤：归并输出项 detail 归一化（去全部空白）后必须被章节原文包含，否则丢弃；
 * <p>规则 2 漏项回补：候选池高置信项（sourceText ⊇ detail 且 weight 非空）若未被任何采纳项
 * detail 归一化互相包含覆盖 → 原样回补，防 LLM 漏项。
 */
public final class ScoreMergeResultGuard {

    private ScoreMergeResultGuard() {
    }

    /** 守卫结果：accepted = 采纳 + 回补（保持顺序）；计数供编排层摘要日志 */
    public record Outcome(
            List<ScoreCandidate> accepted,
            int hallucinationDropped,
            int restoredCount,
            int poolBackedKept) {
    }

    public static Outcome guard(
            List<ScoreCandidate> pool, List<ScoreCandidate> mergedItems, String sectionText) {
        if (mergedItems == null || mergedItems.isEmpty()
                || sectionText == null || sectionText.isBlank()) {
            return new Outcome(List.of(), 0, 0, 0);
        }
        String normalizedSection = normalize(sectionText);
        List<ScoreCandidate> accepted = new ArrayList<>();
        int dropped = 0;
        int poolBacked = 0;
        for (ScoreCandidate item : mergedItems) {
            String detail = item.detail();
            // spec 057 FR-02：语料截断时，语料外但与池身份匹配的真实项不算幻觉（poolBackedKept 计数）
            boolean corpusBacked = detail != null && !detail.isBlank()
                    && normalizedSection.contains(normalize(detail));
            if (!corpusBacked && !isPoolIdentityMatched(item, pool)) {
                dropped++;
                continue;
            }
            // SC-2 强制：归并输出内部互相包含（LLM 未消干净的重复）→ 保留首个，丢弃后者
            if (isCovered(item, accepted)) {
                dropped++;
                continue;
            }
            accepted.add(item);
            if (!corpusBacked) {
                poolBacked++;
            }
        }
        int restored = 0;
        if (pool != null) {
            for (ScoreCandidate candidate : pool) {
                if (!isHighConfidence(candidate) || isCovered(candidate, accepted)) {
                    continue;
                }
                accepted.add(candidate);
                restored++;
            }
        }
        return new Outcome(List.copyOf(accepted), dropped, restored, poolBacked);
    }

    /** spec 057 FR-02：输出项与候选池身份匹配（归一化互含 detail，或被池项原文背书包含）→ 非幻觉 */
    private static boolean isPoolIdentityMatched(ScoreCandidate item, List<ScoreCandidate> pool) {
        if (pool == null || pool.isEmpty()) {
            return false;
        }
        String detail = normalize(item.detail());
        if (detail.isEmpty()) {
            return false;
        }
        for (ScoreCandidate candidate : pool) {
            String pooledDetail = normalize(candidate.detail());
            if (!pooledDetail.isEmpty() && (pooledDetail.contains(detail) || detail.contains(pooledDetail))) {
                return true;
            }
            String sourceText = normalize(candidate.sourceText());
            if (!sourceText.isEmpty() && sourceText.contains(detail)) {
                return true;
            }
        }
        return false;
    }

    private static final BigDecimal MAX_SINGLE_WEIGHT = new BigDecimal("100");

    /** 高置信：有原文背书（sourceText ⊇ detail）且 weight 在合理分值区间 (0, 100] */
    private static boolean isHighConfidence(ScoreCandidate candidate) {
        String detail = candidate.detail();
        String sourceText = candidate.sourceText();
        return detail != null && !detail.isBlank()
                && candidate.weight() != null
                && candidate.weight().compareTo(BigDecimal.ZERO) > 0
                && candidate.weight().compareTo(MAX_SINGLE_WEIGHT) <= 0
                && sourceText != null && !sourceText.isBlank()
                && normalize(sourceText).contains(normalize(detail));
    }

    /** 归一化互相包含即视为同一项（归并输出取更完整版本时覆盖池内残缺版本） */
    private static boolean isCovered(ScoreCandidate candidate, List<ScoreCandidate> accepted) {
        String detail = normalize(candidate.detail());
        for (ScoreCandidate item : accepted) {
            String acceptedDetail = normalize(item.detail());
            if (acceptedDetail.contains(detail) || detail.contains(acceptedDetail)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }
}
