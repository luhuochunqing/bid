package com.xiyu.bid.compliance.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicBusinessLicenseExtractorTest {

    @Test
    @DisplayName("提取标准营业执照条款：包含营业执照与加盖公章要求")
    void testExtract_StandardLicenseClause() {
        String text = """
                第二章 投标人须知
                3.1 投标人资格要求：
                投标人须具备独立法人资格，提供有效的营业执照（副本）复印件加盖公章。
                """;
        BusinessLicenseRequirement req = DeterministicBusinessLicenseExtractor.extract(text);
        assertThat(req).isNotNull();
        assertThat(req.required()).isTrue();
        assertThat(req.allowCertificates()).contains("营业执照");
        assertThat(req.requireOriginalOrCopy()).contains("复印件加盖公章");
        assertThat(req.description()).contains("营业执照");
    }

    @Test
    @DisplayName("提取扩展证照类型：识别事业单位法人证书与执业许可证")
    void testExtract_ExtendedCertTypes() {
        String text = """
                投标人应具有独立承担民事责任的能力：
                提供法人营业执照、事业单位法人证书或执业许可证等证明文件（原件扫描件）。
                """;
        BusinessLicenseRequirement req = DeterministicBusinessLicenseExtractor.extract(text);
        assertThat(req).isNotNull();
        assertThat(req.required()).isTrue();
        assertThat(req.allowCertificates()).contains("营业执照", "事业单位法人证书", "执业许可证");
        assertThat(req.requireOriginalOrCopy()).contains("原件扫描件");
    }

    @Test
    @DisplayName("前附表提及统一社会信用代码但无多余修饰：保底返回营业执照")
    void testExtract_MentioningUsccOnly() {
        String text = "投标人具有统一社会信用代码，符合国家合法经营要求。";
        BusinessLicenseRequirement req = DeterministicBusinessLicenseExtractor.extract(text);
        assertThat(req).isNotNull();
        assertThat(req.allowCertificates()).contains("营业执照");
    }

    @Test
    @DisplayName("文本未提及任何营业执照或主体资格：返回 null")
    void testExtract_NoMentionReturnsNull() {
        String text = "本章节主要说明标包划分、技术规格与售后服务网络要求。";
        BusinessLicenseRequirement req = DeterministicBusinessLicenseExtractor.extract(text);
        assertThat(req).isNull();
    }

    @Test
    @DisplayName("空串与 null：返回 null")
    void testExtract_NullOrBlankReturnsNull() {
        assertThat(DeterministicBusinessLicenseExtractor.extract(null)).isNull();
        assertThat(DeterministicBusinessLicenseExtractor.extract("   ")).isNull();
    }
}
