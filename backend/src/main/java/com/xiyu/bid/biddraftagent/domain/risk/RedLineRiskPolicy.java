package com.xiyu.bid.biddraftagent.domain.risk;

import com.xiyu.bid.biddraftagent.domain.RegexKeywordMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * 废标红线识别策略。
 * 基于关键词将风险点分类为废标红线、一般警告或信息提示。
 */
public class RedLineRiskPolicy {

    private static final List<String> RED_LINE_KEYWORDS = List.of(
            "废标", "无效投标", "取消资格", "拒绝投标", "否决",
            "未按规定装订", "保证金逾期", "未缴纳保证金",
            "业绩造假", "虚假材料", "提供虚假", "伪造",
            "授权缺失", "无授权", "授权无效",
            "报价超控制价", "超过预算", "超限价", "超控制价",
            "逾期递交", "迟到", "未按时递交",
            "不符合资格", "资质不符",
            "未签字", "未盖章", "未加盖", "未密封",
            "未提供原件", "未提交"
    );

    private static final List<String> WARNING_KEYWORDS = List.of(
            "注意", "建议", "可能", "风险", "关注",
            "较短", "紧张", "不足", "不完整",
            "缺失", "缺少", "未明确", "不清晰",
            "竞争激烈", "利润低", "成本高"
    );

    public RiskLevel classify(String text) {
        if (text == null || text.isBlank()) {
            return RiskLevel.INFO;
        }
        if (RegexKeywordMatcher.matchesAny(text, RED_LINE_KEYWORDS)) {
            return RiskLevel.RED_LINE;
        }
        if (RegexKeywordMatcher.matchesAny(text, WARNING_KEYWORDS)) {
            return RiskLevel.WARNING;
        }
        return RiskLevel.INFO;
    }

    public List<RiskItem> classifyAll(List<String> riskPoints) {
        if (riskPoints == null || riskPoints.isEmpty()) {
            return List.of();
        }
        List<RiskItem> items = new ArrayList<>();
        for (String rp : riskPoints) {
            items.add(new RiskItem(rp, classify(rp)));
        }
        return items;
    }
}
