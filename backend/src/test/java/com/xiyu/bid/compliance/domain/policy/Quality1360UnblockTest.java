// Input: 1360 原件真实场景与边界用例
// Output: 验证图二资格提取、图三评分留白、响应性差异表、否决警示语、文件组成与保证金不涉及判定
// Pos: compliance/domain/policy/ - 1360 标书核查解除硬编码阻塞纯核心测试 (FP-Java)
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class Quality1360UnblockTest {

    @Test
    @DisplayName("TOC 目录行与后续章节附件不得污染资格条件提取")
    void tocAndLaterSectionsCannotPolluteQualificationConditions() {
        String tenderText = """
                目录
                七、 投标人资格要求 ....................................................- 4 -
                八、 投标文件的语言 ....................................................- 6 -
                
                七、 投标人资格要求
                1. 投标人为中华人民共和国境内合法注册的独立法人。
                2. 投标人须具有有效的中华人民共和国增值电信业务经营许可证（EDI和ICP）。
                
                八、 投标文件的语言
                投标文件应用中文编写。
                
                十、 投标人应提供文件和资料
                第二章
                投标人资格条件相关证明文件
                投标人主体的营业执照及相关认证等
                第二卷 技术标
                包括投标方案等。
                """;
        List<String> conditions = QualificationConditionExtractor.extract(tenderText);
        assertThat(conditions).hasSize(2);
        assertThat(conditions).allMatch(c -> !c.contains("......") && !c.contains("- 4 -") && !c.contains("第二卷") && !c.contains("相关证明文件"));

        var tenderDoc = DocumentContent.fromText("tender.pdf", "hash", tenderText);
        var req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tenderDoc);
        assertThat(req.hasUnresolvedCertificateRequirements()).isFalse();
    }

    @Test
    @DisplayName("整句主语为投标人时，证书子句继承 BIDDER 主体与 MANDATORY 适用性")
    void parentConditionSubjectIsInheritedBySubclauses() {
        String clause = "1.4 投标人须具有成熟的电子商城平台，且具有有效的中华人民共和国增值电信业务经营许可证（EDI和ICP）。";
        var certs = CertificateRequirementPolicy.classify(clause, clause);
        assertThat(certs).isNotEmpty();
        assertThat(certs).allSatisfy(c -> {
            assertThat(c.target()).isEqualTo(QualificationCondition.Target.BIDDER);
            assertThat(c.applicability()).isEqualTo(QualificationCondition.Applicability.MANDATORY);
        });
    }

    @Test
    @DisplayName("条件性补充文件（如授权文件、股权证明）不被误提取为独立证书要求")
    void authorizationLetterInConditionIsNotExtractedAsCertificateRequirement() {
        String text = "如证书及平台非投标人自身拥有，须补充提供：-具备前述平台和经营许可证的单位出具的针对本项目的“授权文件”，证明投标人被授权合法使用该主网站；-股权关系证明资料。";
        var certs = CertificateRequirementPolicy.classify(text, text);
        assertThat(certs).isEmpty();
    }

    @Test
    @DisplayName("正文证书均已检索满足时，一票否决结论精准列出满足项，绝不虚报'未定位到'")
    void allCertificatesSatisfiedDoesNotReportUnlocatedDefect() {
        String p1 = "资质证明文件\n依法取得中华人民共和国增值电信业务经营许可证（EDI和ICP）。";
        DocumentContent bidDoc = DocumentContent.fromText("bid.pdf", "bid_hash", p1);
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "tender_hash", "七、 投标人资格要求\n投标人须具有中华人民共和国增值电信业务经营许可证（EDI和ICP）。");
        TenderRequirements req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tenderDoc);

        Optional<QualityCheckItemResult> result = QualificationVetoPolicy.checkFirstStage(bidDoc, tenderDoc, req, List.of());
        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.get().conclusion()).contains("标书已检索到证书名称：【")
                .doesNotContain("尚未定位到");
    }

    @Test
    @DisplayName("技术方案中的延保协议模板样板留白不连坐触发评分响应占位留白")
    void warrantyAgreementTemplateDoesNotShortCircuitScoringResponse() {
        String pageText = """
                售后服务方案
                我司承诺提供 7×24 小时技术支持与售后服务保障。
                
                附：延保服务协议书
                甲方：西域智慧供应链（上海）股份公司
                乙方：
                西域智慧供应链（上海）股份公司于_____年_____月_____日在（公司）购买________，经双方达成一致，对该产品延长质保期至______年_____月_____日。
                """;
        DocumentContent bidDoc = DocumentContent.fromText("bid.docx", "bid_hash", pageText);
        TenderRequirements req = new TenderRequirements(
                List.of(new TenderRequirements.ScoringItem("售后服务方案", 10.0, "服务方案完整")),
                List.of(), List.of(), List.of(), List.of(), false);

        Optional<QualityCheckItemResult> result = ScoringResponsePolicy.checkFirstStage(bidDoc, req);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("差异表中填报'无差异'时，响应性评审判定为 PASS")
    void differenceTableWithNoDifferencePassesResponsiveness() {
        String bidText = """
                第一卷 商务标
                附件四：差异表
                项目名称：新兴际华物资采购
                差异情况说明：本投标响应完全响应招标文件，无差异。
                """;
        DocumentContent bidDoc = DocumentContent.fromText("bid.docx", "bid_hash", bidText);
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "tender_hash", "投标人须按附件四提供差异表。");

        QualityCheckItemResult result = ResponsivenessReviewPolicy.check(bidDoc, tenderDoc);
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("无实质性负偏离");
    }

    @Test
    @DisplayName("二级供应商管理中'擅自转包将启动退出并处罚'识别为制度规则引用，不误判违规")
    void supplierPenaltyRuleCitationDoesNotTriggerVetoKeyword() {
        String bidText = "对于二级合作单位，如擅自将订单转包给其他单位，则立即启动退出程序，取消资格并进行处罚。";
        DocumentContent bidDoc = DocumentContent.fromText("bid.docx", "bid_hash", bidText);

        QualityCheckItemResult result = SubItemAndAttachmentRules.checkVetoKeyword(bidDoc);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
    }

    @Test
    @DisplayName("包含目录及商务、技术主要卷册时，文件组成判定为 PASS")
    void documentCompletenessWithTocAndCoreSectionsPasses() {
        String bidText = """
                目 录
                第一卷 商务标 ..................................... 1
                第二卷 技术标 ..................................... 10
                
                第一卷 商务标
                投标函及商务文件。
                
                第二卷 技术标
                技术方案及实施细节。
                """;
        DocumentContent bidDoc = DocumentContent.fromText("bid.docx", "bid_hash", bidText);

        QualityCheckItemResult result = SubItemAndAttachmentRules.checkDocumentCompleteness(bidDoc);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("完整");
    }

    @Test
    @DisplayName("招标文件全文未提及投标保证金时，判定为【不涉及】免收通过")
    void tenderWithoutDepositRequirementPassesAsNotApplicable() {
        String tenderText = """
                第一章 招标公告
                新兴际华集团采购项目。
                第二章 投标人须知
                请认真编制投标文件。
                """;
        DocumentContent bidDoc = DocumentContent.fromText("bid.docx", "bid_hash", "投标说明：我方按要求编制标书。");

        QualityCheckItemResult depositResult = SubItemAndAttachmentRules.checkDepositReceipt(bidDoc, tenderText);
        assertThat(depositResult.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(depositResult.conclusion()).contains("【不涉及】");

        QualityCheckItemResult refundResult = SubItemAndAttachmentRules.checkDepositRefund(bidDoc, tenderText);
        assertThat(refundResult.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(refundResult.conclusion()).contains("【不涉及】");
    }
}
