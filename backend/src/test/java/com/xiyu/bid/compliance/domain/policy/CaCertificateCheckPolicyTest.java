package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import com.xiyu.bid.compliance.domain.QualityEvidence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

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
    @DisplayName("CA证书核查：电子标且知识库台账中存在在库有效 CA 锁时判定 PASS")
    void testCheck_WhenInStockCertInKb_Passes() {
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash2", "正文为正常投标技术方案，无需贴证书截图");
        CaCertificateEvidence ca = new CaCertificateEvidence(
                201L, "ENTITY_CA", "公章+法人章", "CFCA",
                LocalDate.now().plusYears(1), "通用交易平台",
                "IN_STOCK", "ACTIVE", "张主管", "李四");
        List<QualityEvidence> kbEvidences = List.of(QualityEvidence.fromKnowledgeMatch(ca.toKnowledgeMatch(true)));

        QualityCheckItemResult result = CaCertificateCheckPolicy.check(doc, CaCertificateRequirement.empty(), kbEvidences);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("在库可用");
        assertThat(result.evidences()).isNotEmpty();
    }

    @Test
    @DisplayName("CA证书反假：标书仅有目录条目但无占位模板，台账未录入时返回 MANUAL_REVIEW")
    void testCheck_WhenOnlyInCatalogDots_AndNoKbRecord_ReturnsManualReview() {
        String catalogText = "目录\n五、CA数字证书及电子签章证明........................58";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash3", catalogText);

        QualityCheckItemResult result = CaCertificateCheckPolicy.check(doc, CaCertificateRequirement.empty());

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("未检索到企业 CA 数字证书台账记录");
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
    @DisplayName("CA证书核查：电子标在台账中未录入任何证书时返回 MANUAL_REVIEW 提示录入")
    void testCheck_WhenNoCertFound_ReturnsManualReview() {
        String normalText = "西域供应链技术服务方案正文与实施进度。";
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash5", normalText);

        QualityCheckItemResult result = CaCertificateCheckPolicy.check(doc, CaCertificateRequirement.empty());

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("未检索到企业 CA 数字证书台账记录");
    }

    @Test
    @DisplayName("CA证书核查：招标文件要求河北CA但知识库无河北CA时判定FAIL并输出结构化比对（招标要求 vs 台账比对）")
    void testCheck_WhenHebeiCaRequired_AndNotInDb_FailsWithStructuredComparison() {
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash6", "技术方案与施工组织计划");
        CaCertificateRequirement req = CaCertificateRequirement.requiredStandard(
                "河北CA", "编制投标文件需使用企业河北 CA，未办理河北 CA 的投标单位，需进行企业河北 CA 注册");

        // 知识库仅有上海与国网 CA，无河北 CA
        CaCertificateEvidence shanghaiCa = new CaCertificateEvidence(
                301L, "ENTITY_CA", "公章", "CFCA",
                LocalDate.now().plusYears(1), "上海市公共资源交易中心",
                "IN_STOCK", "ACTIVE", "王五", "赵六");
        CaCertificateEvidence sgccCa = new CaCertificateEvidence(
                302L, "ENTITY_CA", "公章", "CFCA",
                LocalDate.now().plusYears(1), "国家电网新一代电子商务平台",
                "IN_STOCK", "ACTIVE", "孙七", "周八");

        List<QualityEvidence> kbEvidences = List.of(
                QualityEvidence.fromKnowledgeMatch(shanghaiCa.toKnowledgeMatch(false)),
                QualityEvidence.fromKnowledgeMatch(sgccCa.toKnowledgeMatch(false))
        );

        QualityCheckItemResult result = CaCertificateCheckPolicy.check(doc, req, kbEvidences);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("【招标文件要求】：须使用【河北CA】");
        assertThat(result.conclusion()).contains("【企业CA台账比对】：【未拥有】");
        assertThat(result.conclusion()).contains("【判定结论】：未满足本项目电子投标用锁要求");
        assertThat(result.evidences()).anyMatch(e -> "TENDER".equals(e.sourceType()) && e.quote().contains("河北CA"));
    }
}
