// Input: 商务要求文本列表
// Output: 按六类标签分类的商务条款列表
// Pos: biddraftagent/domain/commercial — 商务条款分类纯核心策略

package com.xiyu.bid.biddraftagent.domain.commercial;

import com.xiyu.bid.biddraftagent.domain.RegexKeywordMatcher;

import java.util.ArrayList;
import java.util.List;

public class CommercialSubTypePolicy {

    private static final List<String> PAYMENT_KEYWORDS = List.of(
            "付款", "支付", "结算", "预付", "定金", "尾款", "进度款",
            "账期", "银行转账", "承兑", "信用证", "电汇"
    );

    private static final List<String> PERFORMANCE_BOND_KEYWORDS = List.of(
            "履约保证金", "投标保证金", "保证金", "保函", "担保",
            "押金", "保证金比例", "保证金金额"
    );

    private static final List<String> DELIVERY_KEYWORDS = List.of(
            "交付", "供货", "交货", "工期", "周期", "期限",
            "到货", "安装调试", "试运行", "验收期",
            "交付时间", "交货地点"
    );

    private static final List<String> WARRANTY_KEYWORDS = List.of(
            "质保", "保修", "售后", "维保", "维护期",
            "保修期", "质保期", "免费保修", "三包"
    );

    private static final List<String> BREACH_KEYWORDS = List.of(
            "违约", "罚款", "赔偿", "罚则", "违约金",
            "滞纳金", "索赔", "处罚", "扣款", "失信",
            "逾期"
    );

    private static final List<String> IP_KEYWORDS = List.of(
            "知识产权", "专利", "著作权", "版权", "商标",
            "技术成果", "所有权", "归属", "保密", "专有技术",
            "源代码", "知识产权归属"
    );

    public CommercialSubType classify(String text) {
        if (text == null || text.isBlank()) {
            return CommercialSubType.PAYMENT_TERMS;
        }
        if (RegexKeywordMatcher.matchesAny(text, PAYMENT_KEYWORDS)) {
            return CommercialSubType.PAYMENT_TERMS;
        }
        if (RegexKeywordMatcher.matchesAny(text, PERFORMANCE_BOND_KEYWORDS)) {
            return CommercialSubType.PERFORMANCE_BOND;
        }
        if (RegexKeywordMatcher.matchesAny(text, BREACH_KEYWORDS)) {
            return CommercialSubType.BREACH_LIABILITY;
        }
        if (RegexKeywordMatcher.matchesAny(text, DELIVERY_KEYWORDS)) {
            return CommercialSubType.DELIVERY_CYCLE;
        }
        if (RegexKeywordMatcher.matchesAny(text, WARRANTY_KEYWORDS)) {
            return CommercialSubType.WARRANTY_PERIOD;
        }
        if (RegexKeywordMatcher.matchesAny(text, IP_KEYWORDS)) {
            return CommercialSubType.IP_OWNERSHIP;
        }
        return CommercialSubType.PAYMENT_TERMS;
    }

    public List<CommercialRequirementItem> classifyAll(List<String> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return List.of();
        }
        List<CommercialRequirementItem> items = new ArrayList<>();
        for (String req : requirements) {
            items.add(new CommercialRequirementItem(req, classify(req)));
        }
        return items;
    }

}
