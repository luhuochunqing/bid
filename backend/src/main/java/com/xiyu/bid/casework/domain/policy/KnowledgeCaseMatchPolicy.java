package com.xiyu.bid.casework.domain.policy;

import com.xiyu.bid.casework.domain.model.KnowledgeCaseMatchCriteria;
import com.xiyu.bid.casework.domain.model.KnowledgeCaseMatchScore;
import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 纯核心：KnowledgeCase 智能匹配评分策略。
 *
 * <p>无 Spring 依赖、无数据库访问、无副作用。
 * 输入候选案例 + 匹配条件，输出结构化评分结果。
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
     */
    public KnowledgeCaseMatchScore score(KnowledgeCaseReadModel candidate, KnowledgeCaseMatchCriteria criteria) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        // 1. 评分项标题相似度（最高权重）
        int titleScore = calculateTitleSimilarity(criteria.scoringItemTitle(), candidate);
        if (titleScore > 0) {
            score += titleScore;
            reasons.add("评分项匹配");
        }

        // 2. 评分项类别一致
        int categoryScore = calculateCategoryScore(criteria, candidate);
        if (categoryScore > 0) {
            score += categoryScore;
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

        String respText = candidate.getResponseText();
        String summary = respText != null && respText.length() > 100
                ? respText.substring(0, 100) + "..."
                : respText;

        return new KnowledgeCaseMatchScore(
                candidate.getId(),
                score,
                label,
                reasons.isEmpty() ? "基础匹配" : String.join("、", reasons),
                highlighted,
                candidate.getScoringPointTitle(),
                candidate.getRequirementRaw(),
                candidate.getResponseText(),
                summary,
                candidate.getSourceProjectName(),
                candidate.getCustomerType(),
                candidate.getProjectType(),
                candidate.getBidResult(),
                candidate.getReuseCount() != null ? candidate.getReuseCount() : 0
        );
    }

    // ------------------------------------------------------------------
    // 私有计算辅助方法
    // ------------------------------------------------------------------

    private int calculateTitleSimilarity(String criteriaTitle, KnowledgeCaseReadModel candidate) {
        if (!TextSimilarityPolicy.hasText(criteriaTitle)) {
            return 0;
        }
        String caseTitle = candidate.getScoringPointTitle();
        String cleanCriteriaTitle = TextSimilarityPolicy.cleanTitle(criteriaTitle);
        String candidateText = (Objects.toString(candidate.getRequirementRaw(), "") + " "
                + Objects.toString(candidate.getResponseText(), "") + " "
                + Objects.toString(candidate.getScoringPointTitle(), "")).toLowerCase(Locale.ROOT);

        // 1. 直接包含与正文命中
        if (TextSimilarityPolicy.hasText(cleanCriteriaTitle) && cleanCriteriaTitle.length() >= 2) {
            String lowerClean = cleanCriteriaTitle.toLowerCase(Locale.ROOT);
            if (TextSimilarityPolicy.hasText(caseTitle) && (caseTitle.equalsIgnoreCase(cleanCriteriaTitle)
                    || caseTitle.contains(cleanCriteriaTitle)
                    || cleanCriteriaTitle.contains(caseTitle))) {
                return MAX_TITLE_SCORE;
            }
            if (candidateText.contains(lowerClean)) {
                return MAX_TITLE_SCORE;
            }
        }

        if (TextSimilarityPolicy.hasText(caseTitle)) {
            double jaccard = TextSimilarityPolicy.jaccardSimilarityOfText(
                    cleanCriteriaTitle.isEmpty() ? criteriaTitle : cleanCriteriaTitle, caseTitle
            );
            return (int) Math.round(jaccard * MAX_TITLE_SCORE);
        }
        return 0;
    }

    private int calculateCategoryScore(KnowledgeCaseMatchCriteria criteria, KnowledgeCaseReadModel candidate) {
        String cat = candidate.getScoringCategory();
        if (!TextSimilarityPolicy.hasText(cat)) {
            return 0;
        }

        String criteriaCat = Objects.toString(criteria.scoringCategory(), "").trim();
        String criteriaTitle = Objects.toString(criteria.scoringItemTitle(), "").trim();

        // 1. 若双方都有明确类别，优先按类别精确/包含匹配
        if (TextSimilarityPolicy.hasText(criteriaCat)) {
            if (criteriaCat.equalsIgnoreCase(cat) || criteriaCat.contains(cat) || cat.contains(criteriaCat)) {
                return CATEGORY_MATCH_SCORE;
            }
            return 0;
        }

        // 2. 当 criteriaCat 为空时，基于纯净标题业务词进行语义推断
        String combined = TextSimilarityPolicy.cleanTitle(criteriaTitle).toLowerCase(Locale.ROOT);
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
