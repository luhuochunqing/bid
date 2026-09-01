package com.xiyu.bid.casework.domain.policy;

import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;

import java.util.ArrayList;
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

    public record ScoreItemContext(
            Long scoreItemId, String code, String dim, String detail,
            String projectType, String customerType, String keyword
    ) {}

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

        // 1. 条款重叠度（最高 40 分）
        int detailScore = calculateDetailOverlap(context.detail(), candidate, matchedKeywords);
        if (detailScore >= 15) {
            score += detailScore;
            reasons.add("评分项条款高度契合");
        } else if (detailScore > 0) {
            score += detailScore;
            reasons.add("条款部分契合");
        }

        // 2. 标题/正文跨层相似度（最高 20 分）
        int titleScore = calculateTitleSimilarity(context, candidate);
        if (titleScore >= 15) {
            score += titleScore;
            reasons.add("评分点匹配");
        } else if (titleScore > 0) {
            score += titleScore;
            reasons.add("评分点部分匹配");
        }

        // 3. 类别一致性与推断（最高 15 分）
        int categoryScore = calculateCategoryScore(context, candidate);
        if (categoryScore > 0) {
            score += categoryScore;
            reasons.add("类别一致");
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

        score = Math.min(score, 100);
        String label = score >= HIGH_QUALITY_THRESHOLD ? "优质"
                : (score >= GOOD_THRESHOLD ? "良好" : "一般");

        return new MatchResult(candidate.getId(), score, label, reasons, new ArrayList<>(matchedKeywords));
    }

    private int calculateDetailOverlap(String detail, KnowledgeCaseReadModel candidate, Set<String> matchedKeywords) {
        if (!hasText(detail)) {
            return 0;
        }

        Set<String> detailTokens = extractTokens(detail);
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

    private int calculateCategoryScore(ScoreItemContext context, KnowledgeCaseReadModel candidate) {
        String cat = candidate.getScoringCategory();
        if (!hasText(cat)) {
            return 0;
        }

        String dim = Objects.toString(context.dim(), "");
        if (dim.equalsIgnoreCase(cat) || dim.contains(cat) || cat.contains(dim)) {
            return CATEGORY_MATCH_SCORE;
        }

        String combined = (cleanTitle(dim) + " " + Objects.toString(context.detail(), "")).toLowerCase(Locale.ROOT);
        String catLower = cat.trim().toLowerCase(Locale.ROOT);

        if (catLower.contains("技术")) {
            if (containsAny(combined, "技术", "方案", "系统", "对接", "能力", "运维", "架构", "安全",
                    "开发", "功能", "接口", "部署", "硬件", "软件", "网络", "测试", "云平台", "数据库", "集成")) {
                return CATEGORY_MATCH_SCORE;
            }
        } else if (catLower.contains("实施") || catLower.contains("服务") || catLower.contains("售后")) {
            if (containsAny(combined, "实施", "服务", "售后", "响应", "保障", "质保", "应急", "培训", "交付")) {
                return CATEGORY_MATCH_SCORE;
            }
        } else if (catLower.contains("商务") || catLower.contains("报价") || catLower.contains("业绩")) {
            if (containsAny(combined, "商务", "报价", "价格", "财务", "审计", "业绩", "纳税", "结算", "支付", "合同",
                    "保证金", "交货期", "付款", "同类", "案例", "开票", "资信")) {
                return CATEGORY_MATCH_SCORE;
            }
        } else if (catLower.contains("资质") || catLower.contains("认证") || catLower.contains("体系")) {
            if (containsAny(combined, "资质", "证书", "认证", "iso", "许可", "执照", "资信", "等级", "体系", "aaa",
                    "专利", "软著", "专精特新", "高新")) {
                return CATEGORY_MATCH_SCORE;
            }
        } else if (catLower.contains("人员") || catLower.contains("团队") || catLower.contains("机构")) {
            if (containsAny(combined, "人员", "团队", "项目经理", "架构师", "职称", "社保", "学历", "履历", "专家",
                    "工程师", "配置", "投入", "骨干", "成员")) {
                return CATEGORY_MATCH_SCORE;
            }
        }

        return 0;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
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

    private Set<String> extractTokens(String text) {
        return TextSimilarityPolicy.extractTokens(text);
    }

    private static boolean hasText(String str) {
        return TextSimilarityPolicy.hasText(str);
    }
}
