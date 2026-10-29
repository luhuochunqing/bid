package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextInclusionVerifierTest {

    @Test
    @DisplayName("字面完全匹配时通过核验，且标记为 EXACT")
    void exactSubstringMatchSucceeds() {
        String doc = "本项目综合评分总分为100分。其中财务状况及抗风险能力满分10分，提供近三年经审计的财务报表。";
        String rule = "提供近三年经审计的财务报表。";

        var result = TextInclusionVerifier.verify(rule, doc);

        assertThat(result.verified()).isTrue();
        assertThat(result.exact()).isTrue();
        assertThat(result.matchedText()).isEqualTo(rule);
    }

    @Test
    @DisplayName("含多余空白换行与中英文标点混排时归一化通过核验")
    void normalizedWhitespaceAndPunctuationMatchSucceeds() {
        String doc = "1. 供应商近三年（2023年-2025年）经营活动现金流为正得 1.5分；\n有两年为正得 1.0分。";
        String rule = "供应商近三年(2023年-2025年)经营活动现金流为正得1.5分; 有两年为正得1.0分。";

        var result = TextInclusionVerifier.verify(rule, doc);

        assertThat(result.verified()).isTrue();
        assertThat(result.normalized()).isTrue();
    }

    @Test
    @DisplayName("规则文本为空或空白时安全返回未核验")
    void emptyOrBlankRuleSafelyHandled() {
        String doc = "一些标书内容";

        var result = TextInclusionVerifier.verify("", doc);
        assertThat(result.verified()).isFalse();

        var resultNull = TextInclusionVerifier.verify(null, doc);
        assertThat(resultNull.verified()).isFalse();
    }

    @Test
    @DisplayName("文档全文为空或空白时安全返回未核验")
    void emptyDocSafelyHandled() {
        var result = TextInclusionVerifier.verify("合法规则", "");
        assertThat(result.verified()).isFalse();
    }

    @Test
    @DisplayName("完全虚构或未出现在标书中的文本核验失败")
    void hallucinatedTextFailsVerification() {
        String doc = "本项目综合评分总分为100分。其中技术标44分。";
        String rule = "投标人须具备ISO9001质量管理体系认证证书得5分。";

        var result = TextInclusionVerifier.verify(rule, doc);

        assertThat(result.verified()).isFalse();
    }
}
