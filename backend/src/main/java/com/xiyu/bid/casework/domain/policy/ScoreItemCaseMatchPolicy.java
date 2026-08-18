package com.xiyu.bid.casework.domain.policy;

import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 纯核心：评分项级案例智能匹配策略。
 *
 * <p>遵循 FP-Java 规范：纯逻辑、无 Spring/DB 依赖、无副作用、100% 可单测。</p>
 * <p>输入当前评分项条款上下文（code, dim, detail, keyword 等）与候选案例，
 * 计算综合匹配得分（0~100）、匹配评级、命中原因与高亮关键词。</p>
 */
public class ScoreItemCaseMatchPolicy {

    private static final int MAX_DETAIL_SIMILARITY_SCORE = 40;
    private static final int MAX_TITLE_SIMILARITY_SCORE = 20;
    private static final int CATEGORY_MATCH_SCORE = 15;
    private static final int PROJECT_TYPE_MATCH_SCORE = 10;
    private static final int CUSTOMER_TYPE_MATCH_SCORE = 5;
    private static final int WON_BONUS = 5;
    private static final int KEYWORD_HIT_BONUS = 5;

    private static final int HIGH_QUALITY_THRESHOLD = 80;
    private static final int GOOD_THRESHOLD = 60;

    /**
     * 评分项匹配上下文输入
     */
    public record ScoreItemContext(
            Long scoreItemId,
            String code,
            String dim,
            String detail,
            String projectType,
            String customerType,
            String keyword
    ) {}

    /**
     * 匹配评估输出结果
     */
    public record MatchResult(
            Long caseId,
            int score,
            String matchLabel,
            List<String> matchReasons,
            List<String> matchedKeywords
    ) {}

    /**
     * 对单个候选案例进行评分项级匹配打分
     */
    public MatchResult evaluate(KnowledgeCaseReadModel candidate, ScoreItemContext context) {
        if (candidate == null) {
            return new MatchResult(null, 0, "一般", List.of(), List.of());
        }

        int score = 0;
        List<String> reasons = new ArrayList<>();
        Set<String> matchedKeywords = new LinkedHashSet<>();

        // 1. 评分项详细条款（detail）与案例要求/应答的深度分词重叠度（最高 40 分）
        int detailScore = calculateDetailOverlap(context.detail(), candidate, matchedKeywords);
        if (detailScore > 0) {
            score += detailScore;
            reasons.add("评分项条款高度契合");
        }

        // 2. 标题/维度相似度（最高 20 分）
        int titleScore = calculateTitleSimilarity(context, candidate);
        if (titleScore > 0) {
            score += titleScore;
            reasons.add("评分点匹配");
        }

        // 3. 评分类别/维度一致性（最高 15 分）
        if (hasText(context.dim()) && hasText(candidate.getScoringCategory())) {
            if (context.dim().equalsIgnoreCase(candidate.getScoringCategory())
                    || context.dim().contains(candidate.getScoringCategory())
                    || candidate.getScoringCategory().contains(context.dim())) {
                score += CATEGORY_MATCH_SCORE;
                reasons.add("类别一致");
            }
        }

        // 4. 项目类型一致性（最高 10 分）
        if (hasText(context.projectType()) && hasText(candidate.getProjectType())
                && context.projectType().equalsIgnoreCase(candidate.getProjectType())) {
            score += PROJECT_TYPE_MATCH_SCORE;
            reasons.add("项目类型一致");
        }

        // 5. 客户类型一致性（最高 5 分）
        if (hasText(context.customerType()) && hasText(candidate.getCustomerType())
                && context.customerType().equalsIgnoreCase(candidate.getCustomerType())) {
            score += CUSTOMER_TYPE_MATCH_SCORE;
            reasons.add("客户类型一致");
        }

        // 6. 额外搜索关键词命中（最高 5 分）
        if (hasText(context.keyword())) {
            int kwScore = checkKeywordHit(context.keyword(), candidate, matchedKeywords);
            if (kwScore > 0) {
                score += kwScore;
                reasons.add("搜索词命中");
            }
        }

        // 7. 中标案例加成（最高 5 分）
        if ("WON".equalsIgnoreCase(candidate.getBidResult())) {
            score += WON_BONUS;
            reasons.add("历史中标案例");
        }

        // 封顶 100 分
        score = Math.min(score, 100);

        String label = score >= HIGH_QUALITY_THRESHOLD ? "优质"
                : (score >= GOOD_THRESHOLD ? "良好" : "一般");

        return new MatchResult(
                candidate.getId(),
                score,
                label,
                reasons,
                new ArrayList<>(matchedKeywords)
        );
    }

    private int calculateDetailOverlap(String detail, KnowledgeCaseReadModel candidate, Set<String> matchedKeywords) {
        if (!hasText(detail)) {
            return 0;
        }

        Set<String> detailTokens = extractTokens(detail);
        if (detailTokens.isEmpty()) {
            return 0;
        }

        String candidateText = (Objects.toString(candidate.getRequirementRaw(), "") + " "
                + Objects.toString(candidate.getResponseText(), "") + " "
                + Objects.toString(candidate.getScoringPointTitle(), "")).toLowerCase(Locale.ROOT);

        int hitCount = 0;
        for (String token : detailTokens) {
            if (token.length() >= 2 && candidateText.contains(token.toLowerCase(Locale.ROOT))) {
                hitCount++;
                if (matchedKeywords.size() < 8) {
                    matchedKeywords.add(token);
                }
            }
        }

        double ratio = (double) hitCount / detailTokens.size();
        return (int) Math.round(ratio * MAX_DETAIL_SIMILARITY_SCORE);
    }

    private int calculateTitleSimilarity(ScoreItemContext context, KnowledgeCaseReadModel candidate) {
        String candidateTitle = candidate.getScoringPointTitle();
        if (!hasText(candidateTitle)) {
            return 0;
        }

        // If candidate title appears in detail or detail appears in candidate title, strong title match
        if (hasText(context.detail()) && (context.detail().contains(candidateTitle) || candidateTitle.contains(context.detail()))) {
            return MAX_TITLE_SIMILARITY_SCORE;
        }

        String queryTitle = (hasText(context.code()) ? context.code() + " " : "")
                + (hasText(context.dim()) ? context.dim() + " " : "")
                + (hasText(context.keyword()) ? context.keyword() + " " : "");

        if (!hasText(queryTitle)) {
            return 0;
        }

        Set<String> queryTokens = extractTokens(queryTitle);
        Set<String> targetTokens = extractTokens(candidateTitle);

        if (queryTokens.isEmpty() || targetTokens.isEmpty()) {
            return 0;
        }

        Set<String> intersection = new HashSet<>(queryTokens);
        intersection.retainAll(targetTokens);

        Set<String> union = new HashSet<>(queryTokens);
        union.addAll(targetTokens);

        double jaccard = union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
        return (int) Math.round(jaccard * MAX_TITLE_SIMILARITY_SCORE);
    }

    private int checkKeywordHit(String keyword, KnowledgeCaseReadModel candidate, Set<String> matchedKeywords) {
        if (!hasText(keyword)) {
            return 0;
        }

        String target = (Objects.toString(candidate.getScoringPointTitle(), "") + " "
                + Objects.toString(candidate.getRequirementRaw(), "") + " "
                + Objects.toString(candidate.getResponseText(), "")).toLowerCase(Locale.ROOT);

        String kw = keyword.trim().toLowerCase(Locale.ROOT);
        if (target.contains(kw)) {
            matchedKeywords.add(keyword.trim());
            return KEYWORD_HIT_BONUS;
        }
        return 0;
    }

    private Set<String> extractTokens(String text) {
        if (!hasText(text)) {
            return Collections.emptySet();
        }

        Set<String> tokens = new LinkedHashSet<>();
        String[] rawWords = text.replaceAll("[\\p{Punct}\\p{Space}]+", " ").trim().split("\\s+");
        for (String word : rawWords) {
            if (word.length() >= 2 && word.length() <= 8) {
                tokens.add(word);
            }
            if (word.matches(".*[\\u4e00-\\u9fa5].*")) {
                for (int i = 0; i <= word.length() - 2; i++) {
                    tokens.add(word.substring(i, i + 2));
                }
                for (int i = 0; i <= word.length() - 3; i++) {
                    tokens.add(word.substring(i, i + 3));
                }
            }
        }
        return tokens;
    }

    private boolean hasText(String str) {
        return str != null && !str.trim().isEmpty();
    }
}
