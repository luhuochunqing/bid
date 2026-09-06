package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CaCertificateCheckPolicyTest {

    @Test
    @DisplayName("CA证书核查：招标未要求或纸质标时直接判定 PASS")
    void testCheck_WhenNotRequired_PassesDirectly() {
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash1", "正文内容");
        CaCertificateRequirement req = CaCertificateRequirement.notRequired("纸质标免收");

        QualityCheckItemResult result = CaCertificateCheckPolicy.check(doc, req);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("未要求 CA 电子签章或数字证书");
    }

    @Test
    @DisplayName("CA证书核查：标书包含真实完备的 CA 数字证书凭证与序列号时判定 PASS")
    void testCheck_WhenValidCertPagePresent_Passes() {
        String certText = """
                附件：CA数字证书认证有效性证明
                颁发机构：中国金融认证中心 (CFCA)
                颁发给：西域智慧供应链（上海）股份公司
                证书序列号：7A8B9C0D1E2F3344
                有效期至：2027年12月31日
                数字证书算法：SM2 国密算法
                电子签章状态：签名验证有效
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash2", certText);

        QualityCheckItemResult result = CaCertificateCheckPolicy.check(doc, CaCertificateRequirement.empty());

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("真实有效的 CA 数字证书");
        assertThat(result.evidences()).isNotEmpty();
    }

    @Test
    @DisplayName("CA证书反假：目录点线不被误认为有效正文，判定 FAIL")
    void testCheck_WhenOnlyInCatalogDots_Fails() {
        String catalogText = "目录\n五、CA数字证书及电子签章证明........................58";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash3", catalogText);

        QualityCheckItemResult result = CaCertificateCheckPolicy.check(doc, CaCertificateRequirement.empty());

        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("仅在目录中检索到 CA 数字证书条目");
    }

    @Test
    @DisplayName("CA证书反假：检测到未填写的占位模板时直接 FAIL 拦截")
    void testCheck_WhenPlaceholderDetected_Fails() {
        String placeholderText = "CA数字证书\n【此处粘贴CA机构颁发的数字证书有效性截图及电子签章证明】";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash4", placeholderText);

        QualityCheckItemResult result = CaCertificateCheckPolicy.check(doc, CaCertificateRequirement.empty());

        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("占位模板");
    }

    @Test
    @DisplayName("CA证书核查：未检测到任何明文证书信息时返回 MANUAL_REVIEW")
    void testCheck_WhenNoCertFound_ReturnsManualReview() {
        String normalText = "西域供应链技术服务方案正文与实施进度。";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash5", normalText);

        QualityCheckItemResult result = CaCertificateCheckPolicy.check(doc, CaCertificateRequirement.empty());

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("未识别到 CA 证书明文字符");
    }
}
