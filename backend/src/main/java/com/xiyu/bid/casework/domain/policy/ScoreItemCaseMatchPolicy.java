package com.xiyu.bid.casework.domain.policy;

import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.xiyu.bid.casework.domain.policy.TextSimilarityPolicy.hasText;

/**
 * 纯核心：评分项级案例智能匹配策略。
 *
 * <p>遵循 FP-Java 规范：纯逻辑、无 Spring/DB 依赖、无副作用、100% 可单测。</p>
 */
public class ScoreItemCaseMatchPolicy {

    private static final int MAX_ENTITY_MATCH_SCORE = 20, ENTITY_SINGLE_HIT_SCORE = 12;
    private static final int PROJECT_TYPE_MATCH_SCORE = 5, CUSTOMER_TYPE_MATCH_SCORE = 3;
    private static final int WON_BONUS = 5, QUALITY_SCORE_BONUS = 5, KEYWORD_HIT_BONUS = 5;
    private static final int RELEVANCE_FOR_FULL_BONUS = 8;
    private static final int HIGH_QUALITY_THRESHOLD = 80, GOOD_THRESHOLD = 60, MAX_KEYWORDS_COUNT = 8;

    public record ScoreItemContext(
            Long scoreItemId, String code, String dim, String detail,
            String projectType, String customerType, String keyword,
            String projectName, String customerName,
            String qualificationRequirementText, List<String> qualificationKeywords
    ) {
        public ScoreItemContext(Long scoreItemId, String code, String dim, String detail, String projectType, String customerType, String keyword) {
            this(scoreItemId, code, dim, detail, projectType, customerType, keyword, null, null, null, List.of());
        }
        public ScoreItemContext(Long scoreItemId, String code, String dim, String detail, String projectType, String customerType, String keyword, String projectName, String customerName) {
            this(scoreItemId, code, dim, detail, projectType, customerType, keyword, projectName, customerName, null, List.of());
        }
    }

    public record MatchResult(
            Long caseId, int score, String matchLabel,
            List<String> matchReasons, List<String> matchedKeywords
    ) {}

    private record EntityMatchResult(int score, List<String> hitEntities) {}

    /**
     * 批量评估：以候选案例集为语料计算评分项条款词的区分度权重（IDF），
     * 使"机制/措施/分析"这类几乎所有案例都含有的泛词不再决定条款契合度。
     */
    public List<MatchResult> evaluateAll(List<? extends KnowledgeCaseReadModel> candidates, ScoreItemContext context) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        ScoreItemCaseTextMatchPolicy.Corpus corpus = ScoreItemCaseTextMatchPolicy.buildCorpus(
                context != null ? context.detail() : null,
                context != null ? context.qualificationRequirementText() : null,
                candidates);
        return candidates.stream().map(candidate -> evaluate(candidate, context, corpus)).toList();
    }

    public MatchResult evaluate(KnowledgeCaseReadModel candidate, ScoreItemContext context) {
        return evaluate(candidate, context, ScoreItemCaseTextMatchPolicy.Corpus.EMPTY);
    }

    private MatchResult evaluate(
            KnowledgeCaseReadModel candidate, ScoreItemContext context, ScoreItemCaseTextMatchPolicy.Corpus corpus) {
        if (candidate == null || context == null) {
            return new MatchResult(candidate != null ? candidate.getId() : null, 0, "一般", List.of(), List.of());
        }

        // 0. 大类冲突一票否决门禁
        if (CaseCategoryMatchPolicy.isCategoryConflict(
                context.dim(), context.detail(),
                candidate.getScoringCategory(), candidate.getScoringPointTitle())) {
            return new MatchResult(candidate.getId(), 0, "一般", List.of("大类冲突一票否决"), List.of());
        }

        String candidateText = ScoreItemCaseTextMatchPolicy.candidateFullText(candidate);
        int score = 0;
        List<String> reasons = new ArrayList<>();

        // 1. 业务子主题对齐匹配（最高 25 分）
        CaseSubTopicPolicy.SubTopic inferredTopic = CaseSubTopicPolicy.inferSubTopic(context.dim(), context.detail());
        CaseSubTopicPolicy.SubTopicMatchResult subTopicResult = CaseSubTopicPolicy.calculateSubTopicScore(
                inferredTopic, candidate.getSubTopic(), candidateText);
        if (subTopicResult.score() > 0) {
            score += subTopicResult.score();
            if (subTopicResult.reason() != null) {
                reasons.add(subTopicResult.reason());
            }
        }

        // 2. 条款重叠度（若存在资格项业绩实质要求，则择优计算条款重叠）
        int detailScore = Math.max(
                ScoreItemCaseTextMatchPolicy.calculateDetailOverlap(context.detail(), candidateText, corpus),
                ScoreItemCaseTextMatchPolicy.calculateDetailOverlap(
                        context.qualificationRequirementText(), candidateText, corpus));
        if (detailScore > 0) {
            score += detailScore;
            reasons.add(detailScore >= 12 ? "评分项条款高度契合" : "条款部分契合");
        }

        // 2.1 资格项业绩实质要求加权（穿透资格项业绩提取，针对品类/企业层级/金额发票佐证加分）
        var qualPerfBonus = QualificationPerformanceRetriever.calculatePerformanceBonus(
                context.qualificationRequirementText(), context.qualificationKeywords(), candidateText);
        if (qualPerfBonus.score() > 0) {
            score += qualPerfBonus.score();
            reasons.addAll(qualPerfBonus.reasons());
        }

        // 3. 标题/正文跨层相似度
        int titleScore = ScoreItemCaseTextMatchPolicy.calculateTitleSimilarity(context, candidate, candidateText);
        if (titleScore > 0) {
            score += titleScore;
            reasons.add(titleScore >= 15 ? "评分点匹配" : "评分点部分匹配");
        }

        // 内容适用性独立于客户/项目背景。背景、置顶和搜索词不能把无关案例变成评分项案例。
        int relevanceScore = score;
        boolean hasItemRequirement = hasText(context.dim()) || hasText(context.detail())
                || hasText(context.qualificationRequirementText());
        if (hasItemRequirement && relevanceScore == 0) {
            return new MatchResult(candidate.getId(), 0, "一般", List.of("与评分项内容不相关"), List.of());
        }

        // 4. 项目专属核心实体匹配（最高 20 分）
        EntityMatchResult entityResult = calculateEntityScore(context, candidate, candidateText);
        score += entityResult.score();
        if (!entityResult.hitEntities().isEmpty()) {
            reasons.add("命中项目专属实体(" + String.join("、", entityResult.hitEntities().stream().limit(2).toList()) + ")");
        }

        // 4.5 搜索词命中：用户显式输入的关键词属于直接查询相关性
        if (hasText(context.keyword()) && candidateText.contains(context.keyword().trim().toLowerCase(Locale.ROOT))) {
            score += KEYWORD_HIT_BONUS;
            reasons.add("搜索词命中");
        }

        // 5~10. 情境加成（类别/项目类型/客户类型/中标/质量）与业务主题无关，
        // 只能在主题相关的案例之间拉开差距：存在评分项要求时，主题相关度不足按比例折算
        int bonus = 0;
        int categoryScore = CaseCategoryMatchPolicy.calculateCategoryScore(
                context.dim(), context.detail(), candidate.getScoringCategory());
        if (categoryScore > 0) {
            bonus += categoryScore;
            reasons.add("类别一致");
        }
        if (hasText(context.projectType()) && hasText(candidate.getProjectType())
                && context.projectType().equalsIgnoreCase(candidate.getProjectType())) {
            bonus += PROJECT_TYPE_MATCH_SCORE;
            reasons.add("项目类型一致");
        }
        if (hasText(context.customerType()) && hasText(candidate.getCustomerType())
                && context.customerType().equalsIgnoreCase(candidate.getCustomerType())) {
            bonus += CUSTOMER_TYPE_MATCH_SCORE;
            reasons.add("客户类型一致");
        }
        if ("WON".equalsIgnoreCase(candidate.getBidResult())) {
            bonus += WON_BONUS;
            reasons.add("历史中标案例");
        }
        if (candidate.getQualityScore() != null && candidate.getQualityScore() >= 80) {
            bonus += QUALITY_SCORE_BONUS;
        }
        if (hasItemRequirement) {
            score += (int) Math.round(bonus * Math.min(1.0, (double) relevanceScore / RELEVANCE_FOR_FULL_BONUS));
        } else {
            score += bonus;
        }

        // 11. 优先级重排与精炼展示关键词
        List<String> prioritizedKeywords = assemblePrioritizedKeywords(context, candidateText, entityResult.hitEntities());
        score = Math.min(score, 100);
        String label = score >= HIGH_QUALITY_THRESHOLD ? "优质" : (score >= GOOD_THRESHOLD ? "良好" : "一般");

        return new MatchResult(candidate.getId(), score, label, reasons, prioritizedKeywords);
    }

    private List<String> assemblePrioritizedKeywords(
            ScoreItemContext context, String candidateText, List<String> projectEntityHits) {
        Set<String> result = new LinkedHashSet<>();
        if (projectEntityHits != null) {
            for (String entity : projectEntityHits) {
                if (result.size() >= MAX_KEYWORDS_COUNT) break;
                if (entity.length() <= 10 && PunctuationEntityExtractor.isQualityEntity(entity)) result.add(entity);
            }
        }
        if (context.qualificationKeywords() != null) {
            for (String qk : context.qualificationKeywords()) {
                if (result.size() >= MAX_KEYWORDS_COUNT) break;
                extractMatchedKeyword(qk, candidateText).ifPresent(result::add);
            }
        }
        List<String> toCheck = new ArrayList<>();
        if (hasText(cleanTitle(context.dim()))) toCheck.add(cleanTitle(context.dim()));
        toCheck.addAll(PunctuationEntityExtractor.extractPunctuationEntities(context.detail(), context.dim()));
        if (hasText(context.keyword())) toCheck.add(context.keyword());
        for (String word : toCheck) {
            if (result.size() >= MAX_KEYWORDS_COUNT) break;
            extractMatchedKeyword(word, candidateText).ifPresent(result::add);
        }
        return new ArrayList<>(result);
    }

    private static Optional<String> extractMatchedKeyword(String keyword, String candidateText) {
        if (!hasText(keyword) || !hasText(candidateText)) {
            return Optional.empty();
        }
        String trimmed = keyword.trim();
        if (trimmed.length() >= 2 && trimmed.length() <= 20
                && candidateText.contains(trimmed.toLowerCase(Locale.ROOT))
                && PunctuationEntityExtractor.isQualityEntity(trimmed)) {
            return Optional.of(trimmed);
        }
        return Optional.empty();
    }

    private static EntityMatchResult calculateEntityScore(
            ScoreItemContext context, KnowledgeCaseReadModel candidate, String candidateText) {
        Set<String> entities = TextSimilarityPolicy.extractEntityTokens(context.projectName(), context.customerName());
        if (entities.isEmpty()) return new EntityMatchResult(0, List.of());

        String candidateProjName = Objects.toString(candidate.getSourceProjectName(), "").toLowerCase(Locale.ROOT);
        String candText = candidateText != null ? candidateText : "";
        List<String> hitEntities = new ArrayList<>();
        for (String entity : entities) {
            if (!PunctuationEntityExtractor.isQualityEntity(entity)) continue;
            String lowerEntity = entity.toLowerCase(Locale.ROOT);
            if (candText.contains(lowerEntity) || candidateProjName.contains(lowerEntity)) {
                hitEntities.add(entity);
            }
        }
        if (!hitEntities.isEmpty()) {
            int score = Math.min(MAX_ENTITY_MATCH_SCORE, hitEntities.size() >= 2 ? MAX_ENTITY_MATCH_SCORE : ENTITY_SINGLE_HIT_SCORE);
            return new EntityMatchResult(score, hitEntities);
        }
        return new EntityMatchResult(0, List.of());
    }

    public static String cleanTitle(String text) {
        return TextSimilarityPolicy.cleanTitle(text);
    }
}
