package com.xiyu.bid.casework.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 纯核心单测：业务子主题推断与加权策略（CaseSubTopicPolicy）。
 */
class CaseSubTopicPolicyTest {

    @ParameterizedTest
    @CsvSource({
            "'7x24小时售后服务保障方案', 'SLA_SERVICE'",
            "'突发事件应急预案与防汛演练', 'EMERGENCY_PLAN'",
            "'7 应急方案', 'EMERGENCY_PLAN'",
            "'风险管理与应急措施', 'EMERGENCY_PLAN'",
            "'现场应急管理保障与响应方案', 'EMERGENCY_PLAN'",
            "'三峡e购商城系统对接与API接口开发', 'INTEGRATION'",
            "'网络安全与三级等保合规防护方案', 'SECURITY_COMPLIANCE'",
            "'区域仓储管理与供应链物流配送方案', 'LOGISTICS_WAREHOUSE'",
            "'产品质量保证与假一赔十验收标准', 'QUALITY_CONTROL'",
            "'项目交付里程碑与阶段进度实施计划', 'DELIVERY_PROGRESS'",
            "'高可用微服务部署架构设计与总体方案', 'SYS_ARCH'",
            "'项目实施团队组织架构与专家人员配置', 'ORG_STAFF'",
            "'商务响应偏离表与付款结算条款', 'COMMERCIAL_TERMS'"
    })
    @DisplayName("通过标题/维度准确推断 10 大招投标核心业务子主题")
    void testInferSubTopic_FromTitle(String dim, String expectedTopicName) {
        CaseSubTopicPolicy.SubTopic topic = CaseSubTopicPolicy.inferSubTopic(dim, "无详情说明");
        assertThat(topic.name()).isEqualTo(expectedTopicName);
    }

    @Test
    @DisplayName("标题无明确关键词时，通过详情全文推断子主题")
    void testInferSubTopic_FromDetailFallback() {
        String dim = "2.1 综合服务要求";
        String detail = "投标人需建立完善的应急演练机制，制定应急预案与备用方案，防范突发事件对业务连续性的影响。";
        CaseSubTopicPolicy.SubTopic topic = CaseSubTopicPolicy.inferSubTopic(dim, detail);
        assertThat(topic).isEqualTo(CaseSubTopicPolicy.SubTopic.EMERGENCY_PLAN);
    }

    @Test
    @DisplayName("无法识别的主题安全降级为 OTHER")
    void testInferSubTopic_OtherFallback() {
        CaseSubTopicPolicy.SubTopic topic = CaseSubTopicPolicy.inferSubTopic("其他杂项条款", "毫无匹配内容");
        assertThat(topic).isEqualTo(CaseSubTopicPolicy.SubTopic.OTHER);
    }

    @Test
    @DisplayName("候选案例包含结构化 sub_topic 且一致时获得精确满分（25分）")
    void testCalculateSubTopicScore_ExactMatch() {
        CaseSubTopicPolicy.SubTopic inferred = CaseSubTopicPolicy.SubTopic.INTEGRATION;
        CaseSubTopicPolicy.SubTopicMatchResult result = CaseSubTopicPolicy.calculateSubTopicScore(
                inferred, "INTEGRATION", "任意正文内容");

        assertThat(result.score()).isEqualTo(CaseSubTopicPolicy.SUB_TOPIC_EXACT_MATCH_SCORE);
        assertThat(result.reason()).contains("同类业务子主题(系统对接与数据集成)");
    }

    @Test
    @DisplayName("存量案例无 sub_topic 字段但正文包含子主题关键词时获得文本补偿分（15分）")
    void testCalculateSubTopicScore_TextMatchCompensation() {
        CaseSubTopicPolicy.SubTopic inferred = CaseSubTopicPolicy.SubTopic.EMERGENCY_PLAN;
        CaseSubTopicPolicy.SubTopicMatchResult result = CaseSubTopicPolicy.calculateSubTopicScore(
                inferred, null, "本方案制定了完善的应急预案体系以应对各类风险");

        assertThat(result.score()).isEqualTo(CaseSubTopicPolicy.SUB_TOPIC_TEXT_MATCH_SCORE);
        assertThat(result.reason()).contains("业务主题部分契合(应急预案与突发事件响应)");
    }

    @Test
    @DisplayName("存量案例被标记为 OTHER 时，正文或标题包含应急方案等关键词仍可穿透获得 15 分补偿分")
    void testCalculateSubTopicScore_OtherSubTopicCompensation() {
        CaseSubTopicPolicy.SubTopic inferred = CaseSubTopicPolicy.SubTopic.EMERGENCY_PLAN;
        CaseSubTopicPolicy.SubTopicMatchResult result = CaseSubTopicPolicy.calculateSubTopicScore(
                inferred, "OTHER", "（7）风险管理与应急方案 针对突发事件制定完善的应急处置机制");

        assertThat(result.score()).isEqualTo(CaseSubTopicPolicy.SUB_TOPIC_TEXT_MATCH_SCORE);
        assertThat(result.reason()).contains("业务主题部分契合(应急预案与突发事件响应)");
    }

    @Test
    @DisplayName("子主题不匹配或推断为 OTHER 时得 0 分且无理由")
    void testCalculateSubTopicScore_MismatchOrOther() {
        CaseSubTopicPolicy.SubTopicMatchResult otherResult = CaseSubTopicPolicy.calculateSubTopicScore(
                CaseSubTopicPolicy.SubTopic.OTHER, "SYS_ARCH", "系统架构正文");
        assertThat(otherResult.score()).isEqualTo(0);
        assertThat(otherResult.reason()).isNull();

        CaseSubTopicPolicy.SubTopicMatchResult mismatchResult = CaseSubTopicPolicy.calculateSubTopicScore(
                CaseSubTopicPolicy.SubTopic.SLA_SERVICE, "SYS_ARCH", "无售后内容的正文");
        assertThat(mismatchResult.score()).isEqualTo(0);
        assertThat(mismatchResult.reason()).isNull();
    }

    @Test
    @DisplayName("商品管理方案与定价机制推断为 PRODUCT_PRICING 子主题")
    void testInferSubTopic_ProductPricing() {
        assertThat(CaseSubTopicPolicy.inferSubTopic("商品管理方案", "SKU数量、商品的定价机制、价格管控机制"))
                .isEqualTo(CaseSubTopicPolicy.SubTopic.PRODUCT_PRICING);
        assertThat(CaseSubTopicPolicy.inferSubTopic("技术部分", "包含商品管理、定价机制与价格对标分析"))
                .isEqualTo(CaseSubTopicPolicy.SubTopic.PRODUCT_PRICING);
    }
}
