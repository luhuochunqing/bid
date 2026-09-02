package com.xiyu.bid.casework.domain.policy;

import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static com.xiyu.bid.casework.domain.policy.TextSimilarityPolicy.cleanTitle;
import static com.xiyu.bid.casework.domain.policy.TextSimilarityPolicy.extractTokens;
import static com.xiyu.bid.casework.domain.policy.TextSimilarityPolicy.hasText;

/**
 * 纯核心：评分项级案例智能匹配策略。
 *
 * <p>遵循 FP-Java 规范：纯逻辑、无 Spring/DB 依赖、无副作用、100% 可单测。</p>
 */
public class ScoreItemCaseMatchPolicy {

    private static final int MAX_DETAIL_SIMILARITY_SCORE = 30;
    private static final int MAX_TITLE_SIMILARITY_SCORE = 20;
    private static final int MAX_ENTITY_MATCH_SCORE = 25;
    private static final int ENTITY_SINGLE_HIT_SCORE = 15;
    private static final int CATEGORY_MATCH_SCORE = 15;
    private static final int PROJECT_TYPE_MATCH_SCORE = 10;
    private static final int CUSTOMER_TYPE_MATCH_SCORE = 5;
    private static final int WON_BONUS = 5;
    private static final int KEYWORD_HIT_BONUS = 5;

    private static final int HIGH_QUALITY_THRESHOLD = 80;
    private static final int GOOD_THRESHOLD = 60;

    public record ScoreItemContext(
            Long scoreItemId, String code, String dim, String detail,
            String projectType, String customerType, String keyword,
            String projectName, String customerName
    ) {
        public ScoreItemContext(
                Long scoreItemId, String code, String dim, String detail,
                String projectType, String customerType, String keyword
        ) {
            this(scoreItemId, code, dim, detail, projectType, customerType, keyword, null, null);
        }
    }

    public record MatchResult(
            Long caseId, int score, String matchLabel,
            List<String> matchReasons, List<String> matchedKeywords
    ) {}

    public MatchResult evaluate(KnowledgeCaseReadModel candidate, ScoreItemContext context) {
        if (candidate == null) {
            return new MatchResult(null, 0, "一般", List.of(), List.of());
        }

        int score = 0;
        List<String> reasons = new ArrayList<>();
        Set<String> matchedKeywords = new LinkedHashSet<>();

        // 1. 项目专属核心实体/关键词匹配（最高 25 分，断层式区分同类项目）
        int entityScore = calculateEntityScore(context, candidate, matchedKeywords, reasons);
        score += entityScore;

        // 2. 条款重叠度（过滤通用停用词，最高 30 分）
        int detailScore = calculateDetailOverlap(context.detail(), candidate, matchedKeywords);
        if (detailScore >= 15) {
            score += detailScore;
            reasons.add("评分项条款高度契合");
        } else if (detailScore > 0) {
            score += detailScore;
            reasons.add("条款部分契合");
        }

        // 3. 标题/正文跨层相似度（最高 20 分）
        int titleScore = calculateTitleSimilarity(context, candidate);
        if (titleScore >= 15) {
            score += titleScore;
            reasons.add("评分点匹配");
        } else if (titleScore > 0) {
            score += titleScore;
            reasons.add("评分点部分匹配");
        }

        // 4. 类别一致性与推断（最高 15 分）
        int categoryScore = CaseCategoryMatchPolicy.calculateCategoryScore(
                context.dim(), context.detail(), candidate.getScoringCategory()
        );
        if (categoryScore > 0) {
            score += categoryScore;
            reasons.add("类别一致");
        }

        // 5. 项目类型一致性（最高 10 分）
        if (hasText(context.projectType()) && hasText(candidate.getProjectType())
                && context.projectType().equalsIgnoreCase(candidate.getProjectType())) {
            score += PROJECT_TYPE_MATCH_SCORE;
            reasons.add("项目类型一致");
        }

        // 6. 客户类型一致性（最高 5 分）
        if (hasText(context.customerType()) && hasText(candidate.getCustomerType())
                && context.customerType().equalsIgnoreCase(candidate.getCustomerType())) {
            score += CUSTOMER_TYPE_MATCH_SCORE;
            reasons.add("客户类型一致");
        }

        // 7. 额外搜索关键词命中（最高 5 分）
        if (hasText(context.keyword())) {
            int kwScore = checkKeywordHit(context.keyword(), candidate, matchedKeywords);
            if (kwScore > 0) {
                score += kwScore;
                reasons.add("搜索词命中");
            }
        }

        // 8. 中标案例加成（最高 5 分）
        if ("WON".equalsIgnoreCase(candidate.getBidResult())) {
            score += WON_BONUS;
            reasons.add("历史中标案例");
        }

        score = Math.min(score, 100);
        String label = score >= HIGH_QUALITY_THRESHOLD ? "优质"
                : (score >= GOOD_THRESHOLD ? "良好" : "一般");

        return new MatchResult(candidate.getId(), score, label, reasons, new ArrayList<>(matchedKeywords));
    }

    private int calculateEntityScore(
            ScoreItemContext context, KnowledgeCaseReadModel candidate,
            Set<String> matchedKeywords, List<String> reasons) {
        Set<String> entities = TextSimilarityPolicy.extractEntityTokens(
                context.projectName(), context.customerName()
        );
        if (entities.isEmpty()) {
            return 0;
        }

        String candidateText = candidateFullText(candidate);
        String candidateProjName = Objects.toString(candidate.getSourceProjectName(), "").toLowerCase(Locale.ROOT);
        int hitCount = 0;
        List<String> hitEntities = new ArrayList<>();

        for (String entity : entities) {
            String lowerEntity = entity.toLowerCase(Locale.ROOT);
            if (candidateText.contains(lowerEntity) || candidateProjName.contains(lowerEntity)) {
                hitCount++;
                hitEntities.add(entity);
                matchedKeywords.add(entity);
            }
        }

        if (hitCount > 0) {
            String hitSummary = String.join("、", hitEntities.stream().limit(2).toList());
            reasons.add("命中项目专属实体(" + hitSummary + ")");
            return Math.min(MAX_ENTITY_MATCH_SCORE, hitCount >= 2 ? MAX_ENTITY_MATCH_SCORE : ENTITY_SINGLE_HIT_SCORE);
        }

        return 0;
    }

    private int calculateDetailOverlap(String detail, KnowledgeCaseReadModel candidate, Set<String> matchedKeywords) {
        if (!hasText(detail)) {
            return 0;
        }

        Set<String> detailTokens = extractTokens(detail).stream()
                .filter(t -> !TextSimilarityPolicy.STOP_WORDS.contains(t.toLowerCase(Locale.ROOT)))
                .collect(java.util.stream.Collectors.toSet());
        if (detailTokens.isEmpty()) {
            return 0;
        }

        String candidateText = candidateFullText(candidate);
        int hitCount = 0;
        for (String token : detailTokens) {
            if (token.length() >= 2 && candidateText.contains(token.toLowerCase(Locale.ROOT))) {
                hitCount++;
                if (matchedKeywords.size() < 8) {
                    matchedKeywords.add(token);
                }
            }
        }

        if (hitCount == 0) {
            return 0;
        }

        double ratio = (double) hitCount / detailTokens.size();
        int scoreByRatio = (int) Math.round(ratio * 2.5 * MAX_DETAIL_SIMILARITY_SCORE);
        int scoreByHits = Math.min(MAX_DETAIL_SIMILARITY_SCORE, hitCount * 3);
        int calculated = Math.max(scoreByRatio, scoreByHits);
        return Math.min(MAX_DETAIL_SIMILARITY_SCORE, Math.max(calculated, 5));
    }

    private int calculateTitleSimilarity(ScoreItemContext context, KnowledgeCaseReadModel candidate) {
        String candidateTitle = candidate.getScoringPointTitle();
        String candidateText = candidateFullText(candidate);
        String cleanDim = cleanTitle(context.dim());
        String cleanCode = cleanTitle(context.code());

        // 若评分项名称为空（项目级推荐模式），尝试对比项目名称与案例来源项目名称
        if (!hasText(cleanDim) && !hasText(context.dim())) {
            if (hasText(context.projectName()) && hasText(candidate.getSourceProjectName())) {
                double jaccard = TextSimilarityPolicy.jaccardSimilarityOfText(context.projectName(), candidate.getSourceProjectName());
                return (int) Math.round(jaccard * MAX_TITLE_SIMILARITY_SCORE);
            }
            return 0;
        }

        // 1. 评分项清洗后的核心名称在候选案例正文/需求/标题中命中
        if (hasText(cleanDim) && cleanDim.length() >= 2) {
            String lowerCleanDim = cleanDim.toLowerCase(Locale.ROOT);
            if (hasText(candidateTitle) && (candidateTitle.equalsIgnoreCase(cleanDim)
                    || candidateTitle.contains(cleanDim) || cleanDim.contains(candidateTitle))) {
                return MAX_TITLE_SIMILARITY_SCORE;
            }
            if (candidateText.contains(lowerCleanDim)) {
                return MAX_TITLE_SIMILARITY_SCORE;
            }
        }

        // 2. 候选案例标题出现在评分项详情或标题中
        if (hasText(candidateTitle)) {
            String lowerCandTitle = candidateTitle.trim().toLowerCase(Locale.ROOT);
            if ((hasText(context.detail()) && context.detail().toLowerCase(Locale.ROOT).contains(lowerCandTitle))
                    || (hasText(context.dim()) && context.dim().toLowerCase(Locale.ROOT).contains(lowerCandTitle))) {
                return MAX_TITLE_SIMILARITY_SCORE;
            }
        }

        // 3. 分词 Jaccard 与正文覆盖度打分
        String queryTitle = (hasText(cleanDim) ? cleanDim + " " : (hasText(context.dim()) ? context.dim() + " " : ""))
                + (hasText(cleanCode) ? cleanCode + " " : "")
                + (hasText(context.keyword()) ? context.keyword() + " " : "");

        if (!hasText(queryTitle)) {
            return 0;
        }

        Set<String> queryTokens = extractTokens(queryTitle);
        Set<String> targetTokens = extractTokens(candidateTitle);
        if (queryTokens.isEmpty()) {
            return 0;
        }

        int score = 0;
        if (!targetTokens.isEmpty()) {
            Set<String> intersection = new HashSet<>(queryTokens);
            intersection.retainAll(targetTokens);
            Set<String> union = new HashSet<>(queryTokens);
            union.addAll(targetTokens);
            double jaccard = union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
            score = (int) Math.round(jaccard * MAX_TITLE_SIMILARITY_SCORE);
        }

        long hitsInCandidate = queryTokens.stream()
                .filter(t -> t.length() >= 2 && candidateText.contains(t.toLowerCase(Locale.ROOT)))
                .count();
        if (hitsInCandidate > 0) {
            double textCoverage = (double) hitsInCandidate / queryTokens.size();
            int textScore = (int) Math.round(textCoverage * (MAX_TITLE_SIMILARITY_SCORE - 2));
            score = Math.max(score, textScore);
        }

        return score;
    }

    private int checkKeywordHit(String keyword, KnowledgeCaseReadModel candidate, Set<String> matchedKeywords) {
        if (!hasText(keyword)) {
            return 0;
        }

        String target = candidateFullText(candidate);
        String kw = keyword.trim().toLowerCase(Locale.ROOT);
        if (target.contains(kw)) {
            matchedKeywords.add(keyword.trim());
            return KEYWORD_HIT_BONUS;
        }
        return 0;
    }

    public static String cleanTitle(String text) {
        return TextSimilarityPolicy.cleanTitle(text);
    }

    private static String candidateFullText(KnowledgeCaseReadModel candidate) {
        return (Objects.toString(candidate.getRequirementRaw(), "") + " "
                + Objects.toString(candidate.getResponseText(), "") + " "
                + Objects.toString(candidate.getScoringPointTitle(), "")).toLowerCase(Locale.ROOT);
    }
}
