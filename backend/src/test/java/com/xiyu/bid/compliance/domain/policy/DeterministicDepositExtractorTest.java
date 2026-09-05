package com.xiyu.bid.compliance.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicDepositExtractorTest {

    @Test
    @DisplayName("全文无任何保证金与保函字样时：确定性判定为免收/不涉及")
    void testExtract_WhenNoDepositMentioned_ReturnsNotRequired() {
        String text = "第一章 采购公告\n项目名称：办公设备采购\n服务周期：1年\n交付地点：上海市";
        TenderRequirements.DepositRequirement req = DeterministicDepositExtractor.extract(text);

        assertThat(req).isNotNull();
        assertThat(req.required()).isFalse();
        assertThat(req.description()).contains("未提及投标保证金或保函要求");
        assertThat(DeterministicDepositExtractor.isDepositNotRequired(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "本项目免收投标保证金，投标人无需缴纳任何费用。",
            "根据政府采购相关政策，本项目不收取投标保证金。",
            "特别说明：免交投标保证金与履约保证金。",
            "本项目免收保证金，采用电子签章加密上传。",
            "经采购人确认，本项目不涉及投标保证金。",
            "本项目不要求缴纳投标保证金，仅需提供投标承诺函。",
            "投标保证金及交付：免缴纳保证金。"
    })
    @DisplayName("自然语言显式免收短语：精准识别为免收")
    void testExtract_WhenExplicitPhrases_ReturnsNotRequired(String tenderText) {
        TenderRequirements.DepositRequirement req = DeterministicDepositExtractor.extract(tenderText);

        assertThat(req).isNotNull();
        assertThat(req.required()).isFalse();
        assertThat(DeterministicDepositExtractor.isDepositNotRequired(tenderText)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "| 1 | 投标保证金 | 无 | 详见须知前附表 |",
            "前附表第 3.4 条：投标保证金：不适用。",
            "关键要素表：\n投标保证金：免收\n投标有效期：90天",
            "投标保证金（元）：0元",
            "投标保证金: 0.00元",
            "投标保证金 | 0 |",
            "投标保证金：未要求"
    })
    @DisplayName("前附表及条目式表格免收表达（项目1358等真实场景）：精准识别为免收")
    void testExtract_WhenTableFormat_ReturnsNotRequired(String tenderText) {
        TenderRequirements.DepositRequirement req = DeterministicDepositExtractor.extract(tenderText);

        assertThat(req).isNotNull();
        assertThat(req.required()).isFalse();
        assertThat(DeterministicDepositExtractor.isDepositNotRequired(tenderText)).isTrue();
    }

    @Test
    @DisplayName("明确要求缴纳金额：精准提取金额与 required=true")
    void testExtract_WhenRequiredAmount_ReturnsRequired() {
        String text = "前附表：\n投标保证金金额：人民币50000元，汇入指定银行基本账户。";
        TenderRequirements.DepositRequirement req = DeterministicDepositExtractor.extract(text);

        assertThat(req).isNotNull();
        assertThat(req.required()).isTrue();
        assertThat(req.amount()).contains("50000元");
        assertThat(DeterministicDepositExtractor.isDepositNotRequired(text)).isFalse();
    }
}
