package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreItemClassificationPolicyTest {

    @Test
    void preservesExplicitLlmTagsWhenProvided() {
        assertThat(ScoreItemClassificationPolicy.classifyDeliverableType(
                "商品丰富性", "提供电商平台系统数据截图", "TEXT_PROPOSAL"))
                .isEqualTo("TEXT_PROPOSAL");
        assertThat(ScoreItemClassificationPolicy.classifyScoringMode(
                "由多到少进行排序", "OBJECTIVE", "DEDUCTION"))
                .isEqualTo("DEDUCTION");
    }

    @Test
    void classifiesSystemScreenshotAndQuantitativeRankingForSkuItem() {
        String detail = "商品丰富性 | 4 | 根据自本项目招标公告发布以来，投标人提供的面向B端用户电商平台自营商品SKU数量由多到少进行排序，"
                + "第一名得4分，每低1名扣1分，扣完为止。注：(1)提供包含时间的面向B端用户电商平台系统数据截图。";

        assertThat(ScoreItemClassificationPolicy.classifyDeliverableType("商品丰富性", detail, null))
                .isEqualTo("SYSTEM_SCREENSHOT");
        assertThat(ScoreItemClassificationPolicy.classifyScoringMode(detail, "OBJECTIVE", null))
                .isEqualTo("QUANTITATIVE_RANKING");
    }

    @Test
    void keepsRequiredSystemProofWhenAProposalAlsoContainsAnIllustrativeScreenshot() {
        assertThat(ScoreItemClassificationPolicy.classifyDeliverableType(
                "技术方案", "编制技术方案及示例截图，另须提交带时间戳的系统截图。", null))
                .isEqualTo("SYSTEM_SCREENSHOT");
    }

    @Test
    void classifiesQualificationContractAndProposalItems() {
        assertThat(ScoreItemClassificationPolicy.classifyDeliverableType(
                "体系认证", "投标人具有ISO9001认证证书，每有一个得0.5分", null))
                .isEqualTo("QUALIFICATION_CERT");
        assertThat(ScoreItemClassificationPolicy.classifyScoringMode(
                "投标人具有ISO9001认证证书，每有一个得0.5分", "OBJECTIVE", null))
                .isEqualTo("STEPWISE_SCORING");

        assertThat(ScoreItemClassificationPolicy.classifyDeliverableType(
                "信用及安全", "须提供所投标电子商务平台具备由公安部颁发的信息系统安全等级保护备案证明", null))
                .isEqualTo("QUALIFICATION_CERT");

        assertThat(ScoreItemClassificationPolicy.classifyDeliverableType(
                "类似业绩", "投标人近3年具有同类电子商务平台业绩，每有一个得2分，须提供合同复印件", null))
                .isEqualTo("CONTRACT_INVOICE");
        assertThat(ScoreItemClassificationPolicy.classifyScoringMode(
                "投标人近3年具有同类电子商务平台业绩，每有一个得2分", "OBJECTIVE", null))
                .isEqualTo("STEPWISE_SCORING");

        assertThat(ScoreItemClassificationPolicy.classifyDeliverableType(
                "总体服务方案", "评标委员会对投标人提供的总体服务方案进行综合评价", null))
                .isEqualTo("TEXT_PROPOSAL");
        assertThat(ScoreItemClassificationPolicy.classifyScoringMode(
                "评标委员会对投标人提供的总体服务方案进行综合评价", "SUBJECTIVE", null))
                .isEqualTo("SUBJECTIVE_REVIEW");

        assertThat(ScoreItemClassificationPolicy.classifyDeliverableType(
                "项目服务团队", "投标人为本项目配备专门的服务团队，至少包含1名项目负责人", null))
                .isEqualTo("TEXT_PROPOSAL");
        assertThat(ScoreItemClassificationPolicy.classifyScoringMode(
                "投标人为本项目配备专门的服务团队", "SUBJECTIVE", null))
                .isEqualTo("SUBJECTIVE_REVIEW");
    }

    @Test
    void classifiesCommercialAndOtherObjectiveItemsWithoutMislabelingAsProposal() {
        assertThat(ScoreItemClassificationPolicy.classifyDeliverableType(
                "能够提供最长账期", "最长账期6个月（含）以上的，得5分", null))
                .isNull();
        assertThat(ScoreItemClassificationPolicy.classifyScoringMode(
                "最长账期6个月（含）以上的，得5分", "OBJECTIVE", null))
                .isEqualTo("STEPWISE_SCORING");

        assertThat(ScoreItemClassificationPolicy.classifyDeliverableType(
                "报价优惠方案", "提供最优惠的报价方案得5分", null))
                .isNull();

        assertThat(ScoreItemClassificationPolicy.classifyDeliverableType(
                "支持服务", "按承诺的响应速度排序", "OTHER"))
                .isNull();

        assertThat(ScoreItemClassificationPolicy.classifyScoringMode(
                "降价率=（平台面价-给工厂的价格）/平台面价", "OBJECTIVE", null))
                .isEqualTo("QUANTITATIVE_RANKING");
    }
}
