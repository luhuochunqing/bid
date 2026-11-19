package com.xiyu.bid.casework.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CaseCategoryMatchPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "电商平台系统数据截图",
            "提供包含时间的电商平台系统数据截图",
            "系统数据截图",
            "系统截图",
            "电商平台系统后台截图",
            "电商平台系统界面截图",
            "电商平台系统数据",
            "电商平台系统",
            "系统报表",
            "系统统计报表",
            "业务系统截图",
            "平台系统数据截图",
            "系统页面截图",
            "系统网页截图",
            "系统截屏",
            "系统查询截图",
            "系统流水截图",
            "系统功能截图",
            "系统打印件",
            "电商系统数据",
            "销售系统数据",
            "ERP系统截图"
    })
    @DisplayName("客观证明/证据条款中的系统截图词汇不穿透误判为TECHNICAL")
    void testNonTechnicalSystemTermsDoNotClassifyAsTechnical(String text) {
        assertThat(CaseCategoryMatchPolicy.classifyText(text))
                .isNotEqualTo(CaseCategoryMatchPolicy.MacroCategory.TECHNICAL);
    }

    @Test
    @DisplayName("项目38商品丰富性条款因电商平台系统数据截图不应判定为TECHNICAL")
    void testProject38SkuCountClauseDoesNotPenetrateAsTechnical() {
        String detail = """
                根据自本项目招标公告发布以来，投标人提供的面向B端
                用户（企业用户或商家用户）电商平台自营商品SKU数
                量由多到少进行排序，第一名得4分，每低1名扣1分，
                扣完为止。
                注：(1)提供包含时间的面向B端用户（企业用户或商家
                用户)电商平台系统数据截图。提供的数据须真实有效且
                时间在本项目招标公告发布以后，否则不计分，招标人保
                留核查截图数据真实性的权利。(2)自营商品SKU是指
                库存量单位，即可售卖的货品、仓库管理的最小颗粒度和
                一串数字编码。
                """;

        assertThat(CaseCategoryMatchPolicy.classifyText(detail))
                .isEqualTo(CaseCategoryMatchPolicy.MacroCategory.OTHER);
        assertThat(CaseCategoryMatchPolicy.classifyMacroCategory("商品丰富性", detail))
                .isEqualTo(CaseCategoryMatchPolicy.MacroCategory.OTHER);
        assertThat(CaseCategoryMatchPolicy.classifyMacroCategory("自营商品SKU数量", detail))
                .isEqualTo(CaseCategoryMatchPolicy.MacroCategory.OTHER);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "微服务架构高可用实施方案",
            "微服务实施案例",
            "双活容灾配置方案",
            "在线支付对接技术方案",
            "网络安全等级保护方案",
            "电商平台系统技术架构与微服务部署方案"
    })
    @DisplayName("真正的技术方案仍正确判定为TECHNICAL")
    void testGenuineTechnicalRequirementsStillClassifiedAsTechnical(String text) {
        assertThat(CaseCategoryMatchPolicy.classifyText(text))
                .isEqualTo(CaseCategoryMatchPolicy.MacroCategory.TECHNICAL);
    }

    @Test
    @DisplayName("客观证明条款与技术案例类别契合度得分不应产生误加分")
    void testObjectiveProofDoesNotGetCategoryBonusAgainstTechnicalCases() {
        String detail = "自营商品SKU数量由多到少排序。提供电商平台系统数据截图。";
        int score = CaseCategoryMatchPolicy.calculateCategoryScore("商品丰富性", detail, "技术方案");
        assertThat(score).isZero();
    }

    @Test
    @DisplayName("真正的技术评分项与技术案例类别契合度得满分")
    void testTechnicalItemGetsFullCategoryScoreAgainstTechnicalCases() {
        String detail = "微服务技术架构与容灾部署方案";
        int score = CaseCategoryMatchPolicy.calculateCategoryScore("技术方案", detail, "技术方案");
        assertThat(score).isEqualTo(CaseCategoryMatchPolicy.CATEGORY_MATCH_SCORE);
    }
}
