package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class OriginalRuleUnitTextTest {
    @Test void numberedSiblingsInOnePhysicalLineRetainTheirOwnCompleteRules() {
        String first = "1.商品品种：低于标准得0分，\n每增加一定数量加1分，最高10分。\n提供截图证明。";
        String second = "2.投标人能够提供方案得5分。";
        String raw = first + second;
        var a = new OriginalRuleUnitText.Unit("2-1", "商品品种", first);
        var b = new OriginalRuleUnitText.Unit("2-2", "方案承诺", second);
        assertThat(OriginalRuleUnitText.restore(raw, a, List.of(a, b))).isEqualTo(first);
        assertThat(OriginalRuleUnitText.restore(raw, b, List.of(a, b))).isEqualTo(second);
    }
    @Test void siblingAfterWhitespaceFollowingSentenceKeepsPriorRule() {
        String first = "1.验收问题对应扣除相关分数后作为最后得分。";
        String second = "2.平台出现问题时限制相关交易权限。";
        var a = new OriginalRuleUnitText.Unit("10.1", "综合", first);
        var b = new OriginalRuleUnitText.Unit("10.2", "综合", second);
        assertThat(OriginalRuleUnitText.restore(first + " " + second, a, List.of(a, b))).isEqualTo(first);
        assertThat(OriginalRuleUnitText.restore(first + " " + second, b, List.of(a, b))).isEqualTo(second);
    }
    @Test void inlineNumberCannotSplitWhenItDoesNotMatchSiblingIdentity() {
        String first = "1.第一项得5分。";
        String second = "2.第二项得5分。";
        var a = new OriginalRuleUnitText.Unit("2-1", "第一项", first);
        var b = new OriginalRuleUnitText.Unit("2-3", "第二项", second);
        assertThat(OriginalRuleUnitText.restore(first + second, a, List.of(a, b))).isEmpty();
    }
    @Test void siblingStartsAfterAClosedParentheticalSentence() {
        String first = "1.服务得5分。（提供证明材料。）";
        String second = "2.培训得5分。";
        var a = new OriginalRuleUnitText.Unit("9-1", "服务", first);
        var b = new OriginalRuleUnitText.Unit("9-2", "培训", second);
        assertThat(OriginalRuleUnitText.restore(first + second, a, List.of(a, b))).isEqualTo(first);
        assertThat(OriginalRuleUnitText.restore(first + second, b, List.of(a, b))).isEqualTo(second);
    }
    @Test void singleUnitKeepsNumberedMaterialSubscores() {
        String raw="业绩综合评分最高10分。\n1、合同材料（5分）\n提供合同首页及签章页得5分，缺少任一项不得分。\n2、验收证明（5分）\n提供对应验收证明得5分，未提供不得分。";
        var unit=new OriginalRuleUnitText.Unit("业绩","业绩综合评分","最高10分");
        assertThat(OriginalRuleUnitText.restore(raw,unit,List.of(unit))).isEqualTo(raw);
        assertThat(OriginalRuleUnitText.restore(raw,"最高10分")).isEqualTo(raw);
    }
    @Test void independentlyLocatedPeerIdentitiesBoundSharedCellWithoutSplittingInternalNumbers() {
        String first="第一部分：一级集采商品（28分）\n1、合同材料（5分）\n合同首页缺一不得分。\n2、其他条件（23分）\n需验收证明。";
        String second="第二部分：非一级集采商品（12分）\n需提供报价表。";
        var a=new OriginalRuleUnitText.Unit("一级集采商品","价格","28分");
        var b=new OriginalRuleUnitText.Unit("非一级集采商品","价格","12分");
        assertThat(OriginalRuleUnitText.restore(first+"\n"+second,a,List.of(a,b))).isEqualTo(first);
        assertThat(OriginalRuleUnitText.restore(first+"\n"+second,b,List.of(a,b))).isEqualTo(second);
    }
    @Test void namedCandidateHeadingExcludesPreviousCellContinuationButKeepsAllItsConditions() {
        String own="第二部分：非一级集采商品（12分）\n1、材料要求（5分）\n合同首页必需。\n2、评分条件（7分）\n未提供不得分。";
        var unit=new OriginalRuleUnitText.Unit("非一级集采商品","价格",own);
        var previous=new OriginalRuleUnitText.Unit("上一项","上一项","上一项第一段规则。\n上一项跨页清单扣分。");
        assertThat(OriginalRuleUnitText.restore("上一项跨页清单扣分。\n"+own,unit,List.of(previous,unit))).isEqualTo(own);
    }
    @Test void candidateNamedQuoteDoesNotDiscardItsOwnPrecedingMaterials() {
        String raw="材料要求：合同首页和签章页必需。\n业绩综合评分最高10分。\n未提交材料不得分。";
        var unit=new OriginalRuleUnitText.Unit("业绩","业绩综合评分","业绩综合评分最高10分。");
        assertThat(OriginalRuleUnitText.restore(raw,unit,List.of(unit))).isEqualTo(raw);
    }
    @Test void peersWithoutSeparateProvableStartsRemainUnresolved() {
        String raw="甲项28分，乙项12分，材料要求相互交错";
        var a=new OriginalRuleUnitText.Unit("甲项","甲项","28分");
        var b=new OriginalRuleUnitText.Unit("乙项","乙项","12分");
        assertThat(OriginalRuleUnitText.restore(raw,a,List.of(a,b))).isEmpty();
    }
    @Test void inlineSiblingsWithBaiduOcrChinesePeriodRetainCompleteRulesWithoutSwallowingTail() {
        String first = "1.商品品种：低于20万种得0分，20万种(含)\n得5分，每增加10万种加1分，满分10分。提\n供网站查询截图或其他证明材料证明。";
        String second = "2。投标人\n能够提供项目解决方案(2分)、能够配合开展物\n料信息及品类整合归纳(3分)。上述两项内容需\n提供对应承诺书，承诺书格式自拟，盖章有效。";
        String raw = first + second;
        var a = new OriginalRuleUnitText.Unit("2.1", "商品类目和商品品种及保障能力和方案", first);
        var b = new OriginalRuleUnitText.Unit("2.2", "商品类目和商品品种及保障能力和方案", second);
        assertThat(OriginalRuleUnitText.restore(raw, a, List.of(a, b))).isEqualTo(first);
        assertThat(OriginalRuleUnitText.restore(raw, b, List.of(a, b))).isEqualTo(second);
    }
    @Test void inlineSiblingsWithOmittedOrdinalOrLineEndOrdinalRetainExactBoundaries() {
        String first = "1.投标人对包钢销售价格不高于向任何第三方销\n售价格，提供承诺书得10分，格式自拟，盖章\n有效。";
        String secondBody = "投标人结算账期必须在90天及以上，\n得5分，提供承诺书，格式自拟，盖章有效。";
        String thirdBody = "投标人能按照协商约定对应推送及上架相关商\n品得5分，提供承诺书，格式自拟，盖章有效。";
        String raw = first + "2。" + secondBody + "3。\n" + thirdBody;
        var u1 = new OriginalRuleUnitText.Unit("6.1", "价格优惠", first);
        var u2 = new OriginalRuleUnitText.Unit("6.2", "价格优惠", secondBody);
        var u3 = new OriginalRuleUnitText.Unit("6.3", "价格优惠", thirdBody);
        assertThat(OriginalRuleUnitText.restore(raw, u1, List.of(u1, u2, u3))).isEqualTo(first);
        assertThat(OriginalRuleUnitText.restore(raw, u2, List.of(u1, u2, u3))).isEqualTo("2。" + secondBody);
        assertThat(OriginalRuleUnitText.restore(raw, u3, List.of(u1, u2, u3))).isEqualTo("3。\n" + thirdBody);

        var u3WithPrefix = new OriginalRuleUnitText.Unit("6.3", "价格优惠", "3。\n" + thirdBody);
        assertThat(OriginalRuleUnitText.restore(raw, u2, List.of(u1, u2, u3WithPrefix))).isEqualTo("2。" + secondBody);
        assertThat(OriginalRuleUnitText.restore(raw, u3WithPrefix, List.of(u1, u2, u3WithPrefix))).isEqualTo("3。\n" + thirdBody);
    }
}
