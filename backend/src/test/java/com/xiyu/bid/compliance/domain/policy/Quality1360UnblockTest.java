// Input: 1360 原件真实场景与边界用例（第一刀+第二刀）
// Output: 验证图二资格提取与实证闭环、图三评分留白、响应性差异表、否决警示语、文件组成、保证金不涉及及图片OCR闭环
// Pos: compliance/domain/policy/ - 1360 标书核查解除硬编码阻塞纯核心测试 (FP-Java)
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualificationDetail;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
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
    @DisplayName("主语含括号关联公司限定（或母子公司/集团公司）时，证书要求仍识别为 BIDDER + MANDATORY")
    void parentConditionSubjectWithAffiliateParentheticalIsMandatoryBidder() {
        String clause = "投标人（或与投标人为母子公司、或同属同一集团公司体系内的公司）须具有成熟的电子商城平台，且具有有效的中华人民共和国增值电信业务经营许可证（EDI和ICP）。";
        var certs = CertificateRequirementPolicy.classify(clause, clause);
        assertThat(certs).hasSize(1);
        assertThat(certs.getFirst().target()).isEqualTo(QualificationCondition.Target.BIDDER);
        assertThat(certs.getFirst().applicability()).isEqualTo(QualificationCondition.Applicability.MANDATORY);
    }

    @Test
    @DisplayName("条件性补充文件（如授权文件、股权证明）不被误提取为独立证书要求")
    void authorizationLetterInConditionIsNotExtractedAsCertificateRequirement() {
        String text = "如证书及平台非投标人自身拥有，须补充提供：-具备前述平台和经营许可证的单位出具的针对本项目的“授权文件”，证明投标人被授权合法使用该主网站；-股权关系证明资料。";
        assertThat(CertificateRequirementPolicy.classify(text, text)).isEmpty();
        assertThat(CertificateRequirementPolicy.isCertificateReference(text)).isFalse();
    }

    @Test
    @DisplayName("标书正文已提供证书编号与有效期限实证时，资质匹配与一票否决直接闭环 PASS")
    void substantiveCertificateFactsInBidAllowPassForQualificationMatchAndVeto() {
        String bidPage = """
                四、西域具有有效的中华人民共和国增值电信业务经营许可证（EDI和ICP）
                | 经营许可证编号 | 合字B2-20200044 |
                | 业务种类 | 在线数据处理与交易处理业务（仅限经营类电子商务）；信息服务业务（仅限互联网信息服务） |
                | 有效期限 | 2025年7月4日至2030年3月28日 |
                """;
        DocumentContent bidDoc = new DocumentContent("bid.docx", "bid_hash", bidPage,
                List.of(new DocumentContent.PageContent(8, bidPage, List.of())));
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "tender_hash",
                "七、 投标人资格要求\n投标人（或与投标人为母子公司、或同属同一集团公司体系内的公司）须具有有效的中华人民共和国增值电信业务经营许可证（EDI和ICP）。");
        TenderRequirements req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tenderDoc);

        Optional<QualityCheckItemResult> vetoResult = QualificationVetoPolicy.checkFirstStage(bidDoc, tenderDoc, req, List.of());
        assertThat(vetoResult).isPresent();
        assertThat(vetoResult.orElseThrow().status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(SemanticEvidenceBoundary.reconcile(vetoResult.orElseThrow(), bidDoc).status())
                .isEqualTo(QualityCheckStatus.PASS);

        List<QualificationDetail> details = QualificationMatchPolicy.buildDetails(
                req.certificateRequirements(), List.of(), bidDoc, LocalDate.of(2026, 11, 7));
        assertThat(details).hasSize(1);
        assertThat(details.getFirst().finding()).isEqualTo(QualificationDetail.Finding.MATCHED_REFERENCE);
        var reconciled = QualificationDetailReconciler.reconcile(
                QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "资质匹配通过", List.of()),
                details, req, bidDoc);
        assertThat(reconciled.status()).isEqualTo(QualityCheckStatus.PASS);
    }

    @Test
    @DisplayName("仅出现证书名称且其他页含无关项目编号与营业期限时，一票否决仍保持待确认不串页放行")
    void bareCertificateNameWithoutFactsStaysManualReview() {
        String p1 = "封面\n项目编号：XY-ZB-2026-0888\n营业执照有效期限：长期";
        String p2 = "资质证明文件\n依法取得中华人民共和国增值电信业务经营许可证（EDI和ICP）。";
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "bid_hash", p1 + "\n" + p2,
                List.of(
                        new DocumentContent.PageContent(1, p1, List.of()),
                        new DocumentContent.PageContent(2, p2, List.of())
                ));
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "tender_hash",
                "七、 投标人资格要求\n投标人须具有中华人民共和国增值电信业务经营许可证（EDI和ICP）。");
        TenderRequirements req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tenderDoc);

        Optional<QualityCheckItemResult> result = QualificationVetoPolicy.checkFirstStage(bidDoc, tenderDoc, req, List.of());
        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.get().conclusion()).contains("标书已检索到证书名称：【")
                .doesNotContain("尚未定位到");
    }

    @Test
    @DisplayName("身份证正反面要素经OCR完整识别后，委托人身份证项可闭环 PASS")
    void completeBothSidesIdFactsPassIdAuthLetterAndBoundary() {
        String idPage = """
                法定代表人身份证明及委托代理人身份证
                姓名：张伟 性别：男 民族：汉 住址：上海市浦东新区某路100号
                公民身份号码：310115198805121234
                中华人民共和国居民身份证 签发机关：上海市公安局浦东分局 有效期限：2020.05.12-2040.05.12
                """;
        DocumentContent bidDoc = new DocumentContent("bid.docx", "bid_hash", idPage,
                List.of(new DocumentContent.PageContent(3, idPage, List.of())),
                List.of(new com.xiyu.bid.compliance.domain.PageImage(0, "image/png", "身份证", 3, 0, 0, "base64")));
        QualityCheckItemResult result = IdAuthLetterCheckPolicy.check(bidDoc);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(SemanticEvidenceBoundary.reconcile(result, bidDoc).status())
                .isEqualTo(QualityCheckStatus.PASS);
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
                新兴际华集团电子商城外部合作电商引入公开招标项目，采购人为新兴际华集团有限公司。
                第二章 投标人须知
                请各投标人按照招标文件第一卷商务标、第二卷技术标、第三卷经济标的格式要求认真编制并按时递交投标文件。
                第三章 评标办法
                本项目采用综合评估法，对商务、技术、价格三部分进行综合评审打分，择优确定中标合作电商。
                第四章 合同主要条款及格式
                双方按框架协议约定履行供货与结算义务。
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
