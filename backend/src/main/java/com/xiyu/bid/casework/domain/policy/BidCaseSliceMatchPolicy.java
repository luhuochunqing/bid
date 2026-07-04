package com.xiyu.bid.casework.domain.policy;

import com.xiyu.bid.casework.domain.model.BidCaseSliceMatchCandidate;
import com.xiyu.bid.casework.domain.model.BidCaseSliceMatchCriteria;
import com.xiyu.bid.casework.domain.model.BidCaseSliceRecommendation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 纯核心：BidCaseSlice 智能匹配精排策略。
 *
 * <p>无 Spring 依赖、无数据库访问、无副作用。</p>
 *
 * <p>召回：向量余弦相似度取 Top-50；精排：综合 cosine、标题 Jaccard、文件类别、
 * 正文充实度、章节层级计算 0~100 分；最后对同一来源项目做集中度截断。</p>
 */
public class BidCaseSliceMatchPolicy {

    private static final int RECALL_TOP_N = 50;

    private static final int COSINE_WEIGHT = 40;
    private static final int TITLE_JACCARD_WEIGHT = 25;
    private static final int LABEL_WEIGHT = 15;
    private static final int RICHNESS_WEIGHT = 10;
    private static final int LEVEL_WEIGHT = 10;

    private static final double MIN_COSINE_FOR_RECALL = 0.0d;

    /**
     * 对候选切片进行召回 + 精排。
     *
     * @param criteria  匹配条件
     * @param candidates 候选切片
     * @param topK      返回条数上限
     * @return 按相关度排序的推荐结果
     */
    public List<BidCaseSliceRecommendation> match(
            BidCaseSliceMatchCriteria criteria,
            List<BidCaseSliceMatchCandidate> candidates,
            int topK) {

        if (criteria == null || criteria.queryVector() == null || candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        int effectiveTopK = clampTopK(topK);

        List<ScoredCandidate> recalled = candidates.stream()
                .map(candidate -> scoreRecall(candidate, criteria))
                .filter(scored -> scored.cosine >= MIN_COSINE_FOR_RECALL)
                .sorted(Comparator.comparingDouble(ScoredCandidate::cosine).reversed())
                .limit(RECALL_TOP_N)
                .map(scored -> applyRerank(scored, criteria))
                .sorted(Comparator.comparingInt(ScoredCandidate::finalScore)
                        .thenComparingDouble(ScoredCandidate::cosine).reversed())
                .toList();

        return applyConcentration(recalled, effectiveTopK);
    }

    // ------------------------------------------------------------------
    // 召回与精排
    // ------------------------------------------------------------------

    private ScoredCandidate scoreRecall(BidCaseSliceMatchCandidate candidate, BidCaseSliceMatchCriteria criteria) {
        double cosine = CosineSimilarityPolicy.compute(criteria.queryVector(), candidate.vector());
        return new ScoredCandidate(candidate, cosine, 0, "");
    }

    private ScoredCandidate applyRerank(ScoredCandidate recalled, BidCaseSliceMatchCriteria criteria) {
        BidCaseSliceMatchCandidate candidate = recalled.candidate;

        int cosineScore = (int) Math.round(recalled.cosine * COSINE_WEIGHT);
        int titleScore = calculateTitleScore(criteria.queryTokens(), candidate.title());
        int labelScore = calculateLabelScore(criteria.preferredLabel(), candidate.docxLabel());
        int richnessScore = calculateRichnessScore(candidate.paraCount());
        int levelScore = calculateLevelScore(candidate.level());

        int finalScore = Math.min(100, cosineScore + titleScore + labelScore + richnessScore + levelScore);
        String reason = buildMatchReason(cosineScore, titleScore, labelScore, richnessScore, levelScore);

        return new ScoredCandidate(candidate, recalled.cosine, finalScore, reason);
    }

    // ------------------------------------------------------------------
    // 业务打分项
    // ------------------------------------------------------------------

    private int calculateTitleScore(Set<String> queryTokens, String title) {
        if (queryTokens == null || queryTokens.isEmpty() || !hasText(title)) {
            return 0;
        }
        Set<String> titleTokens = tokenSet(title);
        if (titleTokens.isEmpty()) {
            return 0;
        }
        int intersection = 0;
        for (String token : queryTokens) {
            if (titleTokens.contains(token)) {
                intersection++;
            }
        }
        int union = queryTokens.size() + titleTokens.size() - intersection;
        if (union == 0) {
            return 0;
        }
        double jaccard = (double) intersection / union;
        return (int) Math.round(jaccard * TITLE_JACCARD_WEIGHT);
    }

    private int calculateLabelScore(String preferredLabel, String docxLabel) {
        if (!hasText(preferredLabel) || !hasText(docxLabel)) {
            return 0;
        }
        return preferredLabel.equalsIgnoreCase(docxLabel) ? LABEL_WEIGHT : 0;
    }

    private int calculateRichnessScore(int paraCount) {
        if (paraCount >= 5) {
            return RICHNESS_WEIGHT;
        }
        if (paraCount >= 3) {
            return RICHNESS_WEIGHT / 2;
        }
        return 0;
    }

    private int calculateLevelScore(int level) {
        return level <= 2 ? LEVEL_WEIGHT : 0;
    }

    private String buildMatchReason(int cosineScore, int titleScore, int labelScore,
                                     int richnessScore, int levelScore) {
        List<String> reasons = new ArrayList<>();
        if (cosineScore > 0) {
            reasons.add("语义相似");
        }
        if (titleScore > 0) {
            reasons.add("标题匹配");
        }
        if (labelScore == LABEL_WEIGHT) {
            reasons.add("文件类别一致");
        }
        if (richnessScore > 0) {
            reasons.add("内容充实");
        }
        if (levelScore > 0) {
            reasons.add("章节层级优先");
        }
        return reasons.isEmpty() ? "基础匹配" : String.join("、", reasons);
    }

    // ------------------------------------------------------------------
    // 集中度截断：同一项目不超过 topK 的 30%（至少 1 条）
    // ------------------------------------------------------------------

    private List<BidCaseSliceRecommendation> applyConcentration(List<ScoredCandidate> ranked, int topK) {
        int maxPerProject = Math.max(1, (int) Math.ceil(topK * 0.3));
        Map<String, Integer> projectCounts = new HashMap<>();
        List<BidCaseSliceRecommendation> result = new ArrayList<>(topK);

        for (ScoredCandidate scored : ranked) {
            String projectDir = scored.candidate.projectDir();
            int count = projectCounts.getOrDefault(projectDir, 0);
            if (count >= maxPerProject) {
                continue;
            }
            projectCounts.put(projectDir, count + 1);
            result.add(toRecommendation(scored));
            if (result.size() >= topK) {
                break;
            }
        }

        return result;
    }

    private BidCaseSliceRecommendation toRecommendation(ScoredCandidate scored) {
        BidCaseSliceMatchCandidate c = scored.candidate;
        return new BidCaseSliceRecommendation(
                c.id(),
                c.projectDir(),
                c.docxFile(),
                c.docxLabel(),
                c.title(),
                c.textPreview(),
                c.textLength(),
                c.paraCount(),
                scored.cosine,
                scored.finalScore,
                scored.reason
        );
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private int clampTopK(int topK) {
        if (topK < 1) {
            return 20;
        }
        return Math.min(topK, 50);
    }

    private Set<String> tokenSet(String text) {
        Set<String> tokens = new java.util.HashSet<>();
        if (!hasText(text)) {
            return tokens;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String segment : normalized.split("\\s+|[，。、；：！？\"'（）【】]")) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!containsCjk(trimmed) && trimmed.length() >= 2) {
                tokens.add(trimmed);
            } else {
                for (int i = 0; i < trimmed.length() - 1; i++) {
                    tokens.add(trimmed.substring(i, i + 2));
                }
            }
        }
        return tokens;
    }

    private boolean containsCjk(String text) {
        return text.codePoints().anyMatch(cp -> {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
            return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
        });
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private record ScoredCandidate(
            BidCaseSliceMatchCandidate candidate,
            double cosine,
            int finalScore,
            String reason
    ) {
    }
}
