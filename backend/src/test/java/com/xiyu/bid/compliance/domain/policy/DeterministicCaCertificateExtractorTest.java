package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicCaCertificateExtractorTest {

    @Test
    @DisplayName("提取器：识别纸质标免除信号时，输出 required=false")
    void testExtract_WhenPaperBid_ReturnsNotRequired() {
        String text = "本项目为纸质投标文件递交，投标人需携带纸质标书至开标现场，无需CA。";
        DocumentContent doc = DocumentContent.fromText("tender.pdf", "hash1", text);

        CaCertificateRequirement req = DeterministicCaCertificateExtractor.extract(doc);

        assertThat(req).isNotNull();
        assertThat(req.required()).isFalse();
        assertThat(req.description()).contains("纸质标");
    }

    @Test
    @DisplayName("提取器：提取平台名称与 CA 证书要求")
    void testExtract_WhenElectronicPlatform_ExtractsPlatformAndFlags() {
        String text = """
                前附表：
                本项目采用全流程电子招投标，通过浙江省公共资源交易平台进行。
                投标人须办理 CA 数字证书，提供 CA 证书复印件及有效性证明，并加盖电子签章。
                """;
        DocumentContent doc = DocumentContent.fromText("tender.pdf", "hash2", text);

        CaCertificateRequirement req = DeterministicCaCertificateExtractor.extract(doc);

        assertThat(req).isNotNull();
        assertThat(req.required()).isTrue();
        assertThat(req.platformName()).contains("浙江省公共资源交易平台");
        assertThat(req.requireCertificateCopy()).isTrue();
        assertThat(req.requireElectronicSeal()).isTrue();
    }

    @Test
    @DisplayName("提取器：未提及任何 CA 或电子化内容时返回 null")
    void testExtract_WhenNoCaMentioned_ReturnsNull() {
        String text = "本项目采购一批普通办公用品，交付期限为 30 天。";
        DocumentContent doc = DocumentContent.fromText("tender.pdf", "hash3", text);

        CaCertificateRequirement req = DeterministicCaCertificateExtractor.extract(doc);

        assertThat(req).isNull();
    }

    @Test
    @DisplayName("契约融合：确定性规则硬事实优先于大模型生成内容")
    void testRequirement_FuseWithDeterministic() {
        CaCertificateRequirement llm = new CaCertificateRequirement(true, null, false, false, "LLM提取描述");
        CaCertificateRequirement deterministic = new CaCertificateRequirement(true, "政采云", true, true, "确定性描述");

        CaCertificateRequirement fused = llm.fuseWithDeterministic(deterministic);

        assertThat(fused.platformName()).isEqualTo("政采云");
        assertThat(fused.requireCertificateCopy()).isTrue();
        assertThat(fused.requireElectronicSeal()).isTrue();
        assertThat(fused.description()).isEqualTo("确定性描述");
    }

    @Test
    @DisplayName("提取器：识别各省专用 CA 锁（如河北 CA、企业河北 CA）并提取为平台/CA名称")
    void testExtract_WhenHebeiCaSpecified_ExtractsHebeiCa() {
        String text = "编制投标文件需使用企业河北 CA，未办理河北 CA 的投标单位，需进行企业河北 CA 注册。CA 注册有一定周期，请及时办理以免影响本次项目。在线办理地址为：";
        DocumentContent doc = DocumentContent.fromText("tender.pdf", "hash_hebei", text);

        CaCertificateRequirement req = DeterministicCaCertificateExtractor.extract(doc);

        assertThat(req).isNotNull();
        assertThat(req.required()).isTrue();
        assertThat(req.platformName()).contains("河北");
        assertThat(req.platformName()).contains("CA");
    }
}
