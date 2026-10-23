package com.xiyu.bid.casework.domain.policy;

import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static com.xiyu.bid.casework.domain.policy.TextSimilarityPolicy.extractTokens;
import static com.xiyu.bid.casework.domain.policy.TextSimilarityPolicy.hasText;

/**
 * 纯核心：评分项级案例智能匹配策略。
 *
 * <p>遵循 FP-Java 规范：纯逻辑、无 Spring/DB 依赖、无副作用、100% 可单测。</p>
 */
public class ScoreItemCaseMatchPolicy {

    private static final int MAX_DETAIL_SIMILARITY_SCORE = 25, MAX_TITLE_SIMILARITY_SCORE = 20;
    private static final int MAX_ENTITY_MATCH_SCORE = 20, ENTITY_SINGLE_HIT_SCORE = 12;
    private static final int PROJECT_TYPE_MATCH_SCORE = 5, CUSTOMER_TYPE_MATCH_SCORE = 3;
    private static final int WON_BONUS = 5, QUALITY_SCORE_BONUS = 5, KEYWORD_HIT_BONUS = 5;
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

    public MatchResult evaluate(KnowledgeCaseReadModel candidate, ScoreItemContext context) {
        if (candidate == null || context == null) {
            return new MatchResult(candidate != null ? candidate.getId() : null, 0, "一般", List.of(), List.of());
        }

        // 0. 大类冲突一票否决门禁
        if (CaseCategoryMatchPolicy.isCategoryConflict(
                context.dim(), context.detail(),
                candidate.getScoringCategory(), candidate.getScoringPointTitle())) {
            return new MatchResult(candidate.getId(), 0, "一般", List.of("大类冲突一票否决"), List.of());
        }

        String candidateText = candidateFullText(candidate);
        int score = 0;
        List<String> reasons = new ArrayList<>();
        Set<String> projectEntityHits = new LinkedHashSet<>();

        // 1. 项目专属核心实体匹配（最高 20 分）
        EntityMatchResult entityResult = calculateEntityScore(context, candidate, candidateText);
        score += entityResult.score();
        if (!entityResult.hitEntities().isEmpty()) {
            projectEntityHits.addAll(entityResult.hitEntities());
            reasons.add("命中项目专属实体(" + String.join("、", entityResult.hitEntities().stream().limit(2).toList()) + ")");
        }

        // 2. 业务子主题对齐匹配（最高 25 分）
        CaseSubTopicPolicy.SubTopic inferredTopic = CaseSubTopicPolicy.inferSubTopic(context.dim(), context.detail());
        CaseSubTopicPolicy.SubTopicMatchResult subTopicResult = CaseSubTopicPolicy.calculateSubTopicScore(
                inferredTopic, candidate.getSubTopic(), candidateText);
        if (subTopicResult.score() > 0) {
            score += subTopicResult.score();
            if (subTopicResult.reason() != null) {
                reasons.add(subTopicResult.reason());
            }
        }

        // 3. 条款重叠度（若存在资格项业绩实质要求，则择优计算条款重叠）
        int detailScore = calculateDetailOverlap(context.detail(), candidateText);
        if (hasText(context.qualificationRequirementText())) {
            int qualDetailScore = calculateDetailOverlap(context.qualificationRequirementText(), candidateText);
            detailScore = Math.max(detailScore, qualDetailScore);
        }
        if (detailScore > 0) {
            score += detailScore;
            reasons.add(detailScore >= 12 ? "评分项条款高度契合" : "条款部分契合");
        }

        // 3.1 资格项业绩实质要求加权（穿透资格项业绩提取，针对品类/企业层级/金额发票佐证加分）
        var qualPerfBonus = QualificationPerformanceRetriever.calculatePerformanceBonus(
                context.qualificationRequirementText(), context.qualificationKeywords(), candidateText);
        if (qualPerfBonus.score() > 0) {
            score += qualPerfBonus.score();
            reasons.addAll(qualPerfBonus.reasons());
        }

        // 4. 标题/正文跨层相似度
        int titleScore = calculateTitleSimilarity(context, candidate, candidateText);
        if (titleScore > 0) {
            score += titleScore;
            reasons.add(titleScore >= 15 ? "评分点匹配" : "评分点部分匹配");
        }

        // 5. 类别一致性与推断
        int categoryScore = CaseCategoryMatchPolicy.calculateCategoryScore(
                context.dim(), context.detail(), candidate.getScoringCategory());
        if (categoryScore > 0) {
            score += categoryScore;
            reasons.add("类别一致");
        }

        // 6. 项目类型一致性
        if (hasText(context.projectType()) && hasText(candidate.getProjectType())
                && context.projectType().equalsIgnoreCase(candidate.getProjectType())) {
            score += PROJECT_TYPE_MATCH_SCORE;
            reasons.add("项目类型一致");
        }

        // 7. 客户类型一致性
        if (hasText(context.customerType()) && hasText(candidate.getCustomerType())
                && context.customerType().equalsIgnoreCase(candidate.getCustomerType())) {
            score += CUSTOMER_TYPE_MATCH_SCORE;
            reasons.add("客户类型一致");
        }

        // 8. 搜索词命中
        if (hasText(context.keyword()) && candidateText.contains(context.keyword().trim().toLowerCase(Locale.ROOT))) {
            score += KEYWORD_HIT_BONUS;
            reasons.add("搜索词命中");
        }

        // 9. 中标案例加成
        if ("WON".equalsIgnoreCase(candidate.getBidResult())) {
            score += WON_BONUS;
            reasons.add("历史中标案例");
        }

        // 10. 案例文本质量加成
        if (candidate.getQualityScore() != null && candidate.getQualityScore() >= 80) {
            score += QUALITY_SCORE_BONUS;
        }

        // 11. 优先级重排与精炼展示关键词
        List<String> prioritizedKeywords = assemblePrioritizedKeywords(context, candidateText, projectEntityHits);
        score = Math.min(score, 100);
        String label = score >= HIGH_QUALITY_THRESHOLD ? "优质" : (score >= GOOD_THRESHOLD ? "良好" : "一般");

        return new MatchResult(candidate.getId(), score, label, reasons, prioritizedKeywords);
    }

    private List<String> assemblePrioritizedKeywords(
            ScoreItemContext context, String candidateText, Set<String> projectEntityHits) {
        Set<String> result = new LinkedHashSet<>();
        if (projectEntityHits != null) {
            for (String entity : projectEntityHits) {
                if (result.size() >= MAX_KEYWORDS_COUNT) break;
                if (entity.length() <= 10 && PunctuationEntityExtractor.isQualityEntity(entity)) result.add(entity);
            }
        }
        // 优先吸纳资格项提取的核心特征词
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

    private int calculateDetailOverlap(String detail, String candidateText) {
        if (!hasText(detail) || !hasText(candidateText)) return 0;
        Set<String> detailTokens = extractTokens(detail).stream()
                .filter(t -> !TextSimilarityPolicy.STOP_WORDS.contains(t.toLowerCase(Locale.ROOT)))
                .collect(java.util.stream.Collectors.toSet());
        if (detailTokens.isEmpty()) return 0;

        int hitCount = 0;
        for (String token : detailTokens) {
            if (token.length() >= 2 && candidateText.contains(token.toLowerCase(Locale.ROOT))) hitCount++;
        }
        if (hitCount == 0) return 0;
        double ratio = (double) hitCount / detailTokens.size();
        int scoreByRatio = (int) Math.round(ratio * 2.5 * MAX_DETAIL_SIMILARITY_SCORE);
        int scoreByHits = Math.min(MAX_DETAIL_SIMILARITY_SCORE, hitCount * 3);
        return Math.min(MAX_DETAIL_SIMILARITY_SCORE, Math.max(Math.max(scoreByRatio, scoreByHits), 5));
    }

    private int calculateTitleSimilarity(ScoreItemContext context, KnowledgeCaseReadModel candidate, String candidateText) {
        String candidateTitle = candidate.getScoringPointTitle();
        String candText = candidateText != null ? candidateText : "";
        String cleanDim = cleanTitle(context.dim());
        String cleanCode = cleanTitle(context.code());

        if (!hasText(cleanDim) && !hasText(context.dim())) {
            if (hasText(context.projectName()) && hasText(candidate.getSourceProjectName())) {
                double jaccard = TextSimilarityPolicy.jaccardSimilarityOfText(context.projectName(), candidate.getSourceProjectName());
                return (int) Math.round(jaccard * MAX_TITLE_SIMILARITY_SCORE);
            }
            return 0;
        }

        if (hasText(cleanDim) && cleanDim.length() >= 2) {
            String lowerCleanDim = cleanDim.toLowerCase(Locale.ROOT);
            if (hasText(candidateTitle) && (candidateTitle.equalsIgnoreCase(cleanDim)
                    || candidateTitle.contains(cleanDim) || cleanDim.contains(candidateTitle))) {
                return MAX_TITLE_SIMILARITY_SCORE;
            }
        }

        if (hasText(candidateTitle)) {
            String lowerCandTitle = candidateTitle.trim().toLowerCase(Locale.ROOT);
            if ((hasText(context.detail()) && context.detail().toLowerCase(Locale.ROOT).contains(lowerCandTitle))
                    || (hasText(context.dim()) && context.dim().toLowerCase(Locale.ROOT).contains(lowerCandTitle))) {
                return MAX_TITLE_SIMILARITY_SCORE;
            }
        }

        String queryTitle = String.join(" ", Stream.of(cleanDim, cleanCode, context.keyword()).filter(TextSimilarityPolicy::hasText).toList());
        if (!hasText(queryTitle)) return 0;

        Set<String> queryTokens = extractTokens(queryTitle);
        Set<String> targetTokens = extractTokens(candidateTitle);
        if (queryTokens.isEmpty()) return 0;

        double jaccard = TextSimilarityPolicy.jaccardSimilarity(queryTokens, targetTokens);
        int score = (int) Math.round(jaccard * MAX_TITLE_SIMILARITY_SCORE);
        long hitsInCandidate = queryTokens.stream()
                .filter(t -> t.length() >= 2 && candText.contains(t.toLowerCase(Locale.ROOT))).count();
        if (hitsInCandidate > 0) {
            double textCoverage = (double) hitsInCandidate / queryTokens.size();
            score = Math.max(score, (int) Math.round(textCoverage * (MAX_TITLE_SIMILARITY_SCORE - 2)));
        }
        return score;
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
