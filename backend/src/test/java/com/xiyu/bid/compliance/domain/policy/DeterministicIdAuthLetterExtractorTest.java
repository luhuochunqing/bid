package com.xiyu.bid.compliance.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicIdAuthLetterExtractorTest {

    @Test
    @DisplayName("提取明确要求身份证正反面及加盖公章")
    void extract_WhenFrontAndBackAndSealRequired_ExtractsCorrectly() {
        String tenderText = """
                第二章 投标人须知
                3.5 资格证明文件
                （1）营业执照副本复印件；
                （2）法定代表人身份证明及授权委托书；
                （3）委托代理人身份证正反面复印件（须加盖公章）；
                """;

        IdAuthLetterRequirement req = DeterministicIdAuthLetterExtractor.extract(tenderText);

        assertThat(req.required()).isTrue();
        assertThat(req.requireFrontAndBack()).isTrue();
        assertThat(req.requireOriginalOrCopy()).contains("加盖公章");
        assertThat(req.description()).contains("委托代理人身份证正反面复印件");
    }

    @Test
    @DisplayName("提取明确要求提供社保证明")
    void extract_WhenSocialSecurityRequired_ExtractsCorrectly() {
        String tenderText = """
                投标人资格条件：
                1. 具有独立法人资格；
                2. 投标代表如为委托代理人的，须提供近3个月依法缴纳的社保证明材料及身份证复印件；
                """;

        IdAuthLetterRequirement req = DeterministicIdAuthLetterExtractor.extract(tenderText);

        assertThat(req.required()).isTrue();
        assertThat(req.requireSocialSecurity()).isTrue();
        assertThat(req.description()).contains("社保证明材料");
    }

    @Test
    @DisplayName("空文本安全回退 standard 默认配置")
    void extract_WhenBlank_ReturnsStandard() {
        IdAuthLetterRequirement req = DeterministicIdAuthLetterExtractor.extract("   ");

        assertThat(req.required()).isTrue();
        assertThat(req.requireFrontAndBack()).isTrue();
    }

    @Test
    @DisplayName("双轨融合：确定性规则硬事实优先")
    void fuseWithDeterministic_HardFactsPrecedence() {
        IdAuthLetterRequirement llmReq = new IdAuthLetterRequirement(
                true, false, false, null, "大模型模糊描述");
        IdAuthLetterRequirement detReq = new IdAuthLetterRequirement(
                true, true, true, "复印件加盖公章", "确定性规则硬事实");

        IdAuthLetterRequirement fused = llmReq.fuseWithDeterministic(detReq);

        assertThat(fused.requireFrontAndBack()).isTrue();
        assertThat(fused.requireSocialSecurity()).isTrue();
        assertThat(fused.requireOriginalOrCopy()).isEqualTo("复印件加盖公章");
        assertThat(fused.description()).isEqualTo("确定性规则硬事实");
    }
}
