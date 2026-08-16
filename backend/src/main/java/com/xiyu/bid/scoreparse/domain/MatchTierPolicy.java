package com.xiyu.bid.scoreparse.domain;

/**
 * 知识库匹配 tier/ratio 纯计算策略（spec 041 contracts/knowledge-match-api.md 通用骨架）。
 *
 * <p>规则：
 * <ul>
 *   <li>命中数为 0 → NONE / 0（FR-024 空结果不抛错）</li>
 *   <li>ratio = min(100, round(命中数 × 100 / 要求数))；要求数 null 或 ≤0 视为 1</li>
 *   <li>tier：存在标记项（过期/即将到期）或应用降级匹配或 ratio &lt; 100 → PARTIAL，否则 FULL</li>
 * </ul>
 */
public final class MatchTierPolicy {

    public static final String TIER_FULL = "FULL";
    public static final String TIER_PARTIAL = "PARTIAL";
    public static final String TIER_NONE = "NONE";

    private MatchTierPolicy() {
    }

    /**
     * @param matchedCount    命中记录数（人员类为符合人数，业绩类为业绩条数，其余为证据条数）
     * @param requiredCount   要求数量（cert/person/project 类携带；null 或 ≤0 视为 1）
     * @param flaggedExist    命中记录中是否存在标记项（过期证书 expired / 即将到期授权 expireSoon）
     * @param degradedApplied 是否应用了降级匹配（仓库备注文本 / 品牌授权范围近似表达）
     */
    public static Outcome evaluate(int matchedCount, Integer requiredCount, boolean flaggedExist, boolean degradedApplied) {
        if (matchedCount <= 0) {
            return new Outcome(TIER_NONE, 0);
        }
        int denominator = (requiredCount == null || requiredCount <= 0) ? 1 : requiredCount;
        int ratio = (int) Math.min(100L, Math.round((double) matchedCount * 100 / denominator));
        String tier = (flaggedExist || degradedApplied || ratio < 100) ? TIER_PARTIAL : TIER_FULL;
        return new Outcome(tier, ratio);
    }

    /** tier 与 matchRatio 的不可变结果。 */
    public record Outcome(String tier, int matchRatio) {
    }
}
