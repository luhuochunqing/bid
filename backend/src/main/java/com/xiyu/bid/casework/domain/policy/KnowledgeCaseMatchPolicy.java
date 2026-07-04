package com.xiyu.bid.casework.domain.policy;

import com.xiyu.bid.casework.domain.model.KnowledgeCaseMatchCriteria;
import com.xiyu.bid.casework.domain.model.KnowledgeCaseMatchScore;
import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 纯核心：KnowledgeCase 智能匹配评分策略。
 *
 * <p>无 Spring 依赖、无数据库访问、无副作用。
 * 输入候选案例 + 匹配条件，输出结构化评分结果。
 *
 * <p>评分规则（总分 100）：
 * <ul>
 *   <li>评分项标题相似度（Jaccard 系数）：最高 40 分</li>
 *   <li>评分项类别一致：+20 分</li>
 *   <li>项目类型一致：+15 分</li>
 *   <li>客户类型一致：+15 分</li>
 *   <li>关键词命中（标题/需求/应答）：最高 15 分</li>
 *   <li>中标案例加成：+5 分</li>
 * </ul>
 */

public class KnowledgeCaseMatchPolicy {

    private static final int MAX_TITLE_SCORE = 40;
    private static final int CATEGORY_MATCH_SCORE = 20;
    private static final int PROJECT_TYPE_MATCH_SCORE = 15;
    private static final int CUSTOMER_TYPE_MATCH_SCORE = 15;
    private static final int MAX_KEYWORD_SCORE = 15;
    private static final int WON_BONUS = 5;
    private static final int HIGH_QUALITY_THRESHOLD = 85;
    private static final int GOOD_THRESHOLD = 60;

    /**
     * 对单个候选案例进行匹配评分。
     *
     * @param candidate 候选案例
     * @param criteria  匹配条件
     * @return 评分结果（包含分数、标签、原因、高亮文本）
     */
    public KnowledgeCaseMatchScore score(KnowledgeCaseReadModel candidate, KnowledgeCaseMatchCriteria criteria) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        // 1. 评分项标题相似度（最高权重）
        int titleScore = calculateTitleSimilarity(criteria.scoringItemTitle(), candidate.getScoringPointTitle());
        if (titleScore > 0) {
            score += titleScore;
            reasons.add("评分项匹配");
        }

        // 2. 评分项类别一致
        if (TextSimilarityPolicy.hasText(criteria.scoringCategory())
                && criteria.scoringCategory().equalsIgnoreCase(candidate.getScoringCategory())) {
            score += CATEGORY_MATCH_SCORE;
            reasons.add("类别一致");
        }

        // 3. 项目类型一致
        if (TextSimilarityPolicy.hasText(criteria.projectType())
                && criteria.projectType().equalsIgnoreCase(candidate.getProjectType())) {
            score += PROJECT_TYPE_MATCH_SCORE;
            reasons.add("项目类型一致");
        }

        // 4. 客户类型一致
        if (TextSimilarityPolicy.hasText(criteria.customerType())
                && criteria.customerType().equalsIgnoreCase(candidate.getCustomerType())) {
            score += CUSTOMER_TYPE_MATCH_SCORE;
            reasons.add("客户类型一致");
        }

        // 5. 关键词命中
        if (TextSimilarityPolicy.hasText(criteria.keyword())) {
            int keywordScore = calculateKeywordOverlap(criteria.keyword(), candidate);
            if (keywordScore > 0) {
                score += keywordScore;
                reasons.add("关键词命中");
            }
        }

        // 6. 中标案例加成
        if ("WON".equalsIgnoreCase(candidate.getBidResult())) {
            score += WON_BONUS;
            reasons.add("中标案例");
        }

        // 封顶 100
        score = Math.min(score, 100);

        String label = score >= HIGH_QUALITY_THRESHOLD ? "优质"
                : (score >= GOOD_THRESHOLD ? "良好" : "一般");

        String highlighted = generateHighlightedText(
                candidate.getResponseText(),
                criteria.keyword(),
                criteria.scoringItemTitle()
        );

        return new KnowledgeCaseMatchScore(
                candidate.getId(),
                score,
                label,
                reasons.isEmpty() ? "基础匹配" : String.join("、", reasons),
                highlighted
        );
    }

    // ------------------------------------------------------------------
    // 私有计算辅助方法
    // ------------------------------------------------------------------

    private int calculateTitleSimilarity(String criteriaTitle, String caseTitle) {
        if (!TextSimilarityPolicy.hasText(criteriaTitle) || !TextSimilarityPolicy.hasText(caseTitle)) {
            return 0;
        }
        double jaccard = TextSimilarityPolicy.jaccardSimilarityOfText(criteriaTitle, caseTitle);
        return (int) Math.round(jaccard * MAX_TITLE_SCORE);
    }

    private int calculateKeywordOverlap(String keyword, KnowledgeCaseReadModel candidate) {
        if (!TextSimilarityPolicy.hasText(keyword)) {
            return 0;
        }
        String kw = keyword.toLowerCase(Locale.ROOT);
        int score = 0;
        if (TextSimilarityPolicy.hasText(candidate.getScoringPointTitle())
                && candidate.getScoringPointTitle().toLowerCase(Locale.ROOT).contains(kw)) {
            score += 10;
        }
        if (TextSimilarityPolicy.hasText(candidate.getRequirementRaw())
                && candidate.getRequirementRaw().toLowerCase(Locale.ROOT).contains(kw)) {
            score += 5;
        }
        if (TextSimilarityPolicy.hasText(candidate.getResponseText())
                && candidate.getResponseText().toLowerCase(Locale.ROOT).contains(kw)) {
            score += 5;
        }
        return Math.min(score, MAX_KEYWORD_SCORE);
    }

    private String generateHighlightedText(String text, String keyword, String scoringTitle) {
        if (!TextSimilarityPolicy.hasText(text)) {
            return text;
        }
        String result = text;
        if (TextSimilarityPolicy.hasText(keyword)) {
            result = highlightTerm(result, keyword, "<mark>");
        }
        if (TextSimilarityPolicy.hasText(scoringTitle)) {
            for (String token : TextSimilarityPolicy.tokenize(scoringTitle)) {
                if (token.length() >= 2) {
                    result = highlightTerm(result, token, "<mark class=\"match-scoring\">");
                }
            }
        }
        return result;
    }

    private String highlightTerm(String text, String term, String openTag) {
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerTerm = term.toLowerCase(Locale.ROOT);
        String closeTag = "</mark>";
        StringBuilder sb = new StringBuilder();
        int last = 0;
        int idx;
        while ((idx = lowerText.indexOf(lowerTerm, last)) >= 0) {
            sb.append(text, last, idx);
            sb.append(openTag).append(text, idx, idx + term.length()).append(closeTag);
            last = idx + term.length();
        }
        sb.append(text.substring(last));
        return sb.toString();
    }
}
