package com.xiyu.bid.compliance.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicAuthorizationExtractorTest {

    @Test
    @DisplayName("提取标准授权书要求：需提供法定代表人证明及授权委托书")
    void testExtract_StandardAuthorizationRequirements() {
        String tenderText = """
                第二章 投标人须知
                3.1 投标文件的组成
                （1）投标函及投标函附录；
                （2）法定代表人身份证明及授权委托书；
                （3）投标保证金回单。
                """;

        AuthorizationRequirement req = DeterministicAuthorizationExtractor.extract(tenderText);

        assertThat(req).isNotNull();
        assertThat(req.required()).isTrue();
        assertThat(req.allowLegalRepresentativeOnly()).isFalse();
        assertThat(req.templateTitle()).contains("法定代表人身份证明及授权委托书");
    }

    @Test
    @DisplayName("提取法定代表人亲签豁免条款：识别法定代表人亲自参加可不提供授权委托书")
    void testExtract_LegalRepresentativeOnlyExemption() {
        String tenderText = """
                投标人须知前附表：
                3.5 授权委托：
                投标人法定代表人亲自签署并参加投标的，无需提供授权委托书，仅需提供法定代表人身份证明。
                由委托代理人参加的，必须提供法定代表人授权委托书。
                """;

        AuthorizationRequirement req = DeterministicAuthorizationExtractor.extract(tenderText);

        assertThat(req).isNotNull();
        assertThat(req.required()).isTrue();
        assertThat(req.allowLegalRepresentativeOnly()).isTrue();
        assertThat(req.description()).contains("无需授权委托书");
    }

    @Test
    @DisplayName("提取自然人免除条款：自然人响应无需提供授权委托书")
    void testExtract_NaturalPersonNotRequired() {
        String tenderText = """
                竞争性谈判须知：
                自然人响应的，无需授权委托书。
                """;

        AuthorizationRequirement req = DeterministicAuthorizationExtractor.extract(tenderText);

        assertThat(req).isNotNull();
        assertThat(req.required()).isFalse();
        assertThat(req.allowLegalRepresentativeOnly()).isTrue();
    }

    @Test
    @DisplayName("招标文件全文未提及授权书时返回 null")
    void testExtract_WhenNotMentioned_ReturnsNull() {
        String tenderText = "第四章 施工规范与设备清单描述。";

        AuthorizationRequirement req = DeterministicAuthorizationExtractor.extract(tenderText);

        assertThat(req).isNull();
    }

    @Test
    @DisplayName("自然人变体免除：自然人可不提供授权委托书亦判定免除")
    void testExtract_NaturalPersonVariant_NotRequired() {
        String tenderText = "投标人须知：自然人可不提供授权委托书。";

        AuthorizationRequirement req = DeterministicAuthorizationExtractor.extract(tenderText);

        assertThat(req).isNotNull();
        assertThat(req.required()).isFalse();
    }

    @Test
    @DisplayName("双轨融合 fuseWithDeterministic：确定性豁免条款与模板优先，融合 LLM 描述")
    void testFuseWithDeterministic() {
        AuthorizationRequirement llmReq = new AuthorizationRequirement(
                true, false, "授权委托书", "LLM 提取的丰富描述说明");
        AuthorizationRequirement detReq = new AuthorizationRequirement(
                true, true, "法定代表人身份证明及授权委托书", "确定性规则提取的说明");

        AuthorizationRequirement fused = AuthorizationRequirement.fuseWithDeterministic(llmReq, detReq);

        assertThat(fused).isNotNull();
        assertThat(fused.allowLegalRepresentativeOnly()).isTrue();
        assertThat(fused.templateTitle()).isEqualTo("法定代表人身份证明及授权委托书");
        assertThat(fused.description()).isEqualTo("LLM 提取的丰富描述说明");
    }
}
