package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessLicenseCheckPolicyTest {

    @Test
    @DisplayName("提供有效且完整的营业执照与18位信用代码时判定PASS")
    void check_WhenCompleteLicenseProvided_ReturnsPass() {
        var pages = List.of(
            new DocumentContent.PageContent(1, "第一章 投标邀请书 投标人资格要求...", List.of()),
            new DocumentContent.PageContent(2, """
                第三章 资格审查资料
                一、营业执照正本复印件
                统一社会信用代码：91310115MA1K4B9X8Y
                名称：上海西域数智供应链有限公司
                类型：有限责任公司
                法定代表人：叶永清
                经营范围：工业品销售、数智化供应链管理系统开发及技术咨询...
                营业期限：2015年03月12日至长期
                登记机关：上海市浦东新区市场监督管理局
                """, List.of())
        );
        var bidDoc = new DocumentContent("投标书.pdf", "hash_license", "", pages);
        var req = new BusinessLicenseRequirement(true, List.of("营业执照"), "复印件", "需提供有效营业执照复印件");

        QualityCheckItemResult result = BusinessLicenseCheckPolicy.check(bidDoc, req);

        assertThat(result.itemCode()).isEqualTo(BidQualityCheckItem.BUSINESS_LICENSE.getCode());
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("已包含营业执照");
        assertThat(result.conclusion()).contains("91310115MA1K4B9X8Y");
        assertThat(result.evidences()).isNotEmpty();
        assertThat(result.evidences().get(0).pageNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("仅在目录页出现营业执照字样且包含点线引导符时拦截，不误判通过，转人工复核")
    void check_WhenTableOfContentsOnly_ReturnsFailOrSuspicious() {
        var pages = List.of(
            new DocumentContent.PageContent(1, """
                目    录
                一、投标函及投标函附录 .................................... 1
                二、法定代表人身份证明 .................................... 3
                三、授权委托书 ............................................ 4
                四、营业执照及资质证书 .................................... 5
                五、财务审计报告 .......................................... 8
                """, List.of())
        );
        var bidDoc = new DocumentContent("投标书.pdf", "hash_toc", "", pages);
        var req = new BusinessLicenseRequirement(true, List.of("营业执照"), "复印件", "提供营业执照");

        QualityCheckItemResult result = BusinessLicenseCheckPolicy.check(bidDoc, req);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("仅在目录页检测到");
    }

    @Test
    @DisplayName("仅提供未替换的占位符模板时拦截并提示模板未填")
    void check_WhenUnfilledTemplate_ReturnsFail() {
        var pages = List.of(
            new DocumentContent.PageContent(5, """
                附件：投标人证照资料
                【此处粘贴营业执照副本复印件】
                （请将营业执照扫描件附于此处并加盖公章）
                统一社会信用代码：____________________
                法定代表人：[填写法定代表人]
                """, List.of())
        );
        var bidDoc = new DocumentContent("投标书.pdf", "hash_template", "", pages);
        var req = new BusinessLicenseRequirement(true, List.of("营业执照"), "复印件", "提供营业执照");

        QualityCheckItemResult result = BusinessLicenseCheckPolicy.check(bidDoc, req);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("占位模板");
        assertThat(result.evidences()).isNotEmpty();
        assertThat(result.evidences().get(0).pageNumber()).isEqualTo(5);
    }

    @Test
    @DisplayName("允许非企业主体凭事业单位法人证书等替代证照通过核查")
    void check_WhenAlternativeCertificateAllowedAndProvided_ReturnsPass() {
        var pages = List.of(
            new DocumentContent.PageContent(3, """
                资质证明文件
                事业单位法人证书
                统一社会信用代码：12100000400001234X
                名称：国家工业信息安全发展研究中心
                宗旨和业务范围：开展工业信息安全技术研究与服务...
                法定代表人：尹丽波
                举办单位：工业和信息化部
                """, List.of())
        );
        var bidDoc = new DocumentContent("投标书.pdf", "hash_cert", "", pages);
        var req = new BusinessLicenseRequirement(true, List.of("营业执照", "事业单位法人证书"), null, "企业提供营业执照，事业单位提供法人证书");

        QualityCheckItemResult result = BusinessLicenseCheckPolicy.check(bidDoc, req);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("已包含事业单位法人证书");
        assertThat(result.conclusion()).contains("12100000400001234X");
        assertThat(result.evidences()).isNotEmpty();
        assertThat(result.evidences().get(0).pageNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("标书中完全缺失证照及代码时判定FAIL")
    void check_WhenNoLicenseFound_ReturnsFail() {
        var pages = List.of(
            new DocumentContent.PageContent(1, "投标报价表 总价：100万元整", List.of()),
            new DocumentContent.PageContent(2, "技术方案及售后服务承诺书...", List.of())
        );
        var bidDoc = new DocumentContent("投标书.pdf", "hash_empty", "", pages);
        var req = new BusinessLicenseRequirement(true, List.of("营业执照"), null, "必须提供营业执照");

        QualityCheckItemResult result = BusinessLicenseCheckPolicy.check(bidDoc, req);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("未检索到营业执照");
    }
}
