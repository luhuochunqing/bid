// Input: 评分标准文本列表
// Output: 按六种标签分类的评分标准列表（价格权重/技术评价/服务评价/资质门槛/综合评分/其他）
// Pos: biddraftagent/domain — 评分标准子类型分类纯核心策略

package com.xiyu.bid.biddraftagent.domain;


import java.util.ArrayList;
import java.util.List;

/**
 * 评分标准子类型分类策略。
 * 基于关键词匹配，将评分标准文本归类为六种标签：
 * 价格权重、技术评价、服务评价、资质门槛、综合评分、其他。
 * 如果无法匹配任何标签，归类为 OTHER（其他）。
 */
public class ScoringCriteriaClassificationPolicy {

    private static final List<String> PRICE_WEIGHT_KEYWORDS = List.of(
            "价格", "报价", "投标报价", "价格分", "报价分",
            "价格权重", "价格占比", "价格评审",
            "低价", "控制价", "预算", "成本",
            "商务报价", "经济分", "经济效益",
            "价格扣除", "价格调整"
    );

    private static final List<String> TECHNICAL_EVALUATION_KEYWORDS = List.of(
            "技术方案", "技术分", "技术评审", "技术指标",
            "技术评分", "技术部分", "技术标",
            "技术响应", "技术实现", "技术架构",
            "整体架构", "系统设计", "设计方案", "设计方法",
            "技术性能", "性能指标"
    );

    private static final List<String> SERVICE_EVALUATION_KEYWORDS = List.of(
            "服务", "售后", "售后服务", "技术支持",
            "响应", "响应时间", "服务响应",
            "维护", "运维", "保修",
            "培训", "服务方案", "服务团队",
            "备件", "服务网点", "本地化服务"
    );

    private static final List<String> QUALIFICATION_THRESHOLD_KEYWORDS = List.of(
            "资质", "资格", "资格条件", "资质要求",
            "许可证", "认证", "资质证书",
            "注册资[本金]", "实收资本",
            "业绩要求", "类似项目业绩",
            "财务要求", "纳税", "社保",
            "人员要求", "负责人资格",
            "信誉要求", "信用"
    );

    private static final List<String> COMPREHENSIVE_SCORE_KEYWORDS = List.of(
            "综合评分", "综合评审", "综合评估",
            "总分", "评分标准", "评审标准",
            "打分", "评分", "评价标准",
            "评分细则", "得分", "评标标准"
    );

    /**
     * 分类单个评分标准文本，返回对应的子类型。
     * 按优先级：价格权重 > 技术评价 > 服务评价 > 资质门槛 > 综合评分 > 其他
     */
    public ScoringCriteriaSubType classify(String text) {
        if (text == null || text.isBlank()) {
            return ScoringCriteriaSubType.OTHER;
        }

        if (RegexKeywordMatcher.matchesAny(text, PRICE_WEIGHT_KEYWORDS)) {
            return ScoringCriteriaSubType.PRICE_WEIGHT;
        }
        if (RegexKeywordMatcher.matchesAny(text, TECHNICAL_EVALUATION_KEYWORDS)) {
            return ScoringCriteriaSubType.TECHNICAL_EVALUATION;
        }
        if (RegexKeywordMatcher.matchesAny(text, SERVICE_EVALUATION_KEYWORDS)) {
            return ScoringCriteriaSubType.SERVICE_EVALUATION;
        }
        if (RegexKeywordMatcher.matchesAny(text, QUALIFICATION_THRESHOLD_KEYWORDS)) {
            return ScoringCriteriaSubType.QUALIFICATION_THRESHOLD;
        }
        if (RegexKeywordMatcher.matchesAny(text, COMPREHENSIVE_SCORE_KEYWORDS)) {
            return ScoringCriteriaSubType.COMPREHENSIVE_SCORE;
        }
        return ScoringCriteriaSubType.OTHER;
    }

    public List<ScoringCriteriaItem> classifyAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<ScoringCriteriaItem> items = new ArrayList<>();
        for (String text : texts) {
            items.add(new ScoringCriteriaItem(text, classify(text)));
        }
        return items;
    }

}
