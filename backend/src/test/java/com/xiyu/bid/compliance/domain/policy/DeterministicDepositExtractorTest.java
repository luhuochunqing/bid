package com.xiyu.bid.compliance.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicDepositExtractorTest {

    @Test
    @DisplayName("全文无任何保证金/担保字样时：审慎返回 null，交由 LLM 或人工复核，绝不武断断言免收")
    void testExtract_WhenNoDepositMentioned_ReturnsNull() {
        String text = "第一章 采购公告\n项目名称：办公设备采购\n服务周期：1年\n交付地点：上海市";
        DepositRequirement req = DeterministicDepositExtractor.extract(text);

        assertThat(req).isNull();
        assertThat(DeterministicDepositExtractor.isDepositNotRequired(text)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "本项目免收投标保证金，投标人无需缴纳任何费用。",
            "根据政府采购相关政策，本项目不收取投标保证金。",
            "特别说明：免交投标保证金与履约保证金。",
            "本项目免收保证金，采用电子签章加密上传。",
            "经采购人确认，本项目不涉及投标保证金。",
            "本项目不要求缴纳投标保证金，仅需提供投标承诺函。",
            "投标保证金及交付：免缴纳保证金。",
            "本项目免收投标担保，无需提供保函。",
            "采购文件特别规定：不收取投标担保。"
    })
    @DisplayName("自然语言显式免收短语（含投标担保）：精准识别为免收")
    void testExtract_WhenExplicitPhrases_ReturnsNotRequired(String tenderText) {
        DepositRequirement req = DeterministicDepositExtractor.extract(tenderText);

        assertThat(req).isNotNull();
        assertThat(req.required()).isFalse();
        assertThat(DeterministicDepositExtractor.isDepositNotRequired(tenderText)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "| 1 | 投标保证金 | 无 | 详见须知前附表 |",
            "| 7.1 | 投标保证金 | 不适用 |",
            "| 7.1 | 投标担保 | 免收 |",
            "| 7.1 | 投标保证金 | 0元 |",
            "前附表第 3.4 条：投标保证金：不适用。",
            "关键要素表：\n投标保证金：免收\n投标有效期：90天",
            "投标保证金（元）：0元",
            "投标保证金: 0.00元",
            "投标保证金 | 0 |",
            "投标保证金：未要求",
            "投标担保：不采用"
    })
    @DisplayName("前附表及条目式表格免收表达（项目1358真实前附表场景）：精准识别为免收")
    void testExtract_WhenTableFormat_ReturnsNotRequired(String tenderText) {
        DepositRequirement req = DeterministicDepositExtractor.extract(tenderText);

        assertThat(req).isNotNull();
        assertThat(req.required()).isFalse();
        assertThat(DeterministicDepositExtractor.isDepositNotRequired(tenderText)).isTrue();
    }

    @Test
    @DisplayName("阿拉伯数字金额：精准提取金额与 required=true")
    void testExtract_WhenRequiredAmount_ReturnsRequired() {
        String text = "前附表：\n投标保证金金额：人民币50000元，汇入指定银行基本账户。";
        DepositRequirement req = DeterministicDepositExtractor.extract(text);

        assertThat(req).isNotNull();
        assertThat(req.required()).isTrue();
        assertThat(req.amount()).contains("50000元");
        assertThat(DeterministicDepositExtractor.isDepositNotRequired(text)).isFalse();
    }

    @Test
    @DisplayName("投标担保与中文大写金额：精准识别需缴纳与中文大写金额")
    void testExtract_WhenChineseAmountAndTenderGuarantee_ReturnsRequired() {
        String text = "3.4 投标担保：本项目要求提交投标担保，投标担保金额为人民币壹拾万元整，应在截标前到达指定账户。";
        DepositRequirement req = DeterministicDepositExtractor.extract(text);

        assertThat(req).isNotNull();
        assertThat(req.required()).isTrue();
        assertThat(req.amount()).contains("壹拾万元整");
        assertThat(DeterministicDepositExtractor.isDepositNotRequired(text)).isFalse();
    }

    @Test
    @DisplayName("交叉验证 fuse：确定性提取到明确金额 vs LLM 幻觉免收时，硬事实金额优先，消除幻觉")
    void testFuse_WhenAmountVsLlmExemption_AmountWins() {
        DepositRequirement llmExemption = DepositRequirement.notRequired("LLM 幻觉认为免收");
        DepositRequirement detAmount = DepositRequirement.required("10万元", null, "前附表明文要求10万元");

        DepositRequirement fused = DepositRequirement.fuse(llmExemption, detAmount);

        assertThat(fused).isNotNull();
        assertThat(fused.required()).isTrue();
        assertThat(fused.amount()).isEqualTo("10万元");
    }

    @Test
    @DisplayName("交叉验证 fuse：冲突且均无具体金额时，审慎合规判需缴纳，绝不盲目偏向免收")
    void testFuse_WhenConflictWithoutAmount_ConservativeWins() {
        DepositRequirement a = DepositRequirement.notRequired("免收");
        DepositRequirement b = new DepositRequirement(true, null, null, "要求缴纳");

        DepositRequirement fused = DepositRequirement.fuse(a, b);

        assertThat(fused).isNotNull();
        assertThat(fused.required()).isTrue();
        assertThat(fused.description()).contains("分歧");
    }

    @Test
    @DisplayName("双轨融合 fuseWithDeterministic：双方均有金额时坚决以确定性原文金额为准，防止 LLM 改写覆盖原文")
    void testFuseWithDeterministic_WhenBothHaveAmount_DeterministicAmountWins() {
        // LLM 提取时发生缩略或改写（如 5万元，附带丰富说明与形式）
        DepositRequirement llmReq = DepositRequirement.required(
                "5万元", "银行保函/网银转账", "投标人应提供银行保函或网银电汇凭证");
        // 确定性规则从原文正则精准提取（如 50,000元）
        DepositRequirement detReq = DepositRequirement.required(
                "50,000元", null, "招标文件条款规定：投标保证金：50,000元");

        DepositRequirement fused = DepositRequirement.fuseWithDeterministic(
                llmReq, detReq);

        assertThat(fused).isNotNull();
        assertThat(fused.required()).isTrue();
        assertThat(fused.amount())
                .as("双有数时坚决以确定性规则从原文提取的金额为准")
                .isEqualTo("50,000元");
        assertThat(fused.forms())
                .as("保留 LLM 提取的更丰富形式")
                .isEqualTo("银行保函/网银转账");
        assertThat(fused.description())
                .as("保留 LLM 提取的更丰富描述")
                .isEqualTo("投标人应提供银行保函或网银电汇凭证");
    }
}
