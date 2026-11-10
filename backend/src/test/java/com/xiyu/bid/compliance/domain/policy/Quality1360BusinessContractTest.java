// Input: 明确业务契约或已标记的历史启发式观察输入
// Output: 保留原测试断言；语料不作为默认门禁
// Pos: compliance/domain/policy - semantic boundary 技术债分层测试
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualificationDetail;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import com.xiyu.bid.compliance.domain.QualityEvidence;
import com.xiyu.bid.compliance.service.TenderRequirementsParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class Quality1360BusinessContractTest {

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"项目经理业绩"})
    void fullScoringFieldBlankRetainsOnlyLocatedFormalFail(String field) {
        for (String blank : List.of("____", "【待填】", "（请填写）")) {
            String quote = field + "：" + blank;
            var fail = QualityCheckItemResult.fail(BidQualityCheckItem.SCORING_RESPONSE,
                    "评分字段未填写", "请补齐", List.of(QualityEvidence.bid("缺陷", 1, quote)));
            for (boolean sample : List.of(false, true)) {
                var bid = DocumentContent.fromText("bid", "hash", (sample ? "合同样例\n" : "") + quote);
                assertThat(SemanticEvidenceBoundary.reconcile(fail, bid).status())
                        .as("%s，样例=%s", quote, sample)
                        .isEqualTo(sample ? QualityCheckStatus.MANUAL_REVIEW : QualityCheckStatus.FAIL);
            }
            var filled = DocumentContent.fromText("bid", "hash", field + "：详见附件一");
            assertThat(SemanticEvidenceBoundary.reconcile(fail, filled).status())
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"项目经理业绩证明"})
    void nonPlanScoringAdmissionRetainsLocatedFail(String object) {
        String quote = "我司本次未提供" + object;
        for (boolean sample : List.of(false, true)) {
            String text = (sample ? "填写示例：\n" : "") + quote;
            var bid = DocumentContent.fromText("bid", "hash", text);
            var fail = QualityCheckItemResult.fail(BidQualityCheckItem.SCORING_RESPONSE,
                    object + "未提供", "请补齐", List.of(QualityEvidence.bid("缺陷", 1, quote)));
            assertThat(SemanticEvidenceBoundary.reconcile(fail, bid).status())
                    .isEqualTo(sample ? QualityCheckStatus.MANUAL_REVIEW : QualityCheckStatus.FAIL);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"本公司未取得ISO9001认证。"})
    void certificateSampleDefectsCannotContaminateFormalStatement(String sample) {
        for (String separator : List.of("\n", "\n\f\n")) {
            String text = "填写示例：\n" + sample + separator + "正式声明：\n本公司具有ISO9001认证。";
            assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证")).isTrue();
            assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(
                    text + "该证书已过期。", "ISO9001认证")).isFalse();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"技术方案：（详见附件一）"})
    void filledReferencesAndParenthesesCannotProveScoringDefect(String text) {
        var bid = new DocumentContent("bid", "hash", text,
                List.of(new DocumentContent.PageContent(3, text, List.of())), List.of());
        var fail = QualityCheckItemResult.fail(BidQualityCheckItem.SCORING_RESPONSE,
                "技术方案存在占位", "请核实", List.of(QualityEvidence.bid("缺陷", 3, text)));
        assertThat(SemanticEvidenceBoundary.reconcile(fail, bid).status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"本次必填技术方案：____"})
    void scoringFailWithLocatedConcreteDefectIsPreserved(String quote) {
        var bid = new DocumentContent("bid", "hash", quote,
                List.of(new DocumentContent.PageContent(2, quote, List.of())), List.of());
        var fail = QualityCheckItemResult.fail(BidQualityCheckItem.SCORING_RESPONSE,
                "本次必填技术方案存在占位或核心内容缺失", "请补齐技术方案",
                List.of(QualityEvidence.bid("技术方案缺陷", 2, quote)));
        assertThat(SemanticEvidenceBoundary.reconcile(fail, bid)).isEqualTo(fail);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"合同样例\n技术方案：____"})
    void scoringFailWithoutConcreteDefectRemainsManual(String text) {
        var bid = new DocumentContent("bid", "hash", text,
                List.of(new DocumentContent.PageContent(2, text, List.of())), List.of());
        var fail = QualityCheckItemResult.fail(BidQualityCheckItem.SCORING_RESPONSE,
                "技术方案缺失", "请核查", List.of(QualityEvidence.bid("评分响应", 2, text)));
        assertThat(SemanticEvidenceBoundary.reconcile(fail, bid).status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        var unlocated = QualityCheckItemResult.fail(BidQualityCheckItem.SCORING_RESPONSE,
                "技术方案缺失", "请核查", List.of());
        assertThat(SemanticEvidenceBoundary.reconcile(unlocated, bid).status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"合同样例\n技术方案：____"})
    void locatedQuoteDoesNotEscapeItsTemplateContext(String text) {
        String quote = text.contains("____") ? "技术方案：____" : text.contains("【") ? "技术方案：【请填写技术方案】" : "我司本次未提供技术方案正文";
        var bid = new DocumentContent("bid", "hash", text,
                List.of(new DocumentContent.PageContent(2, text, List.of())), List.of());
        var fail = QualityCheckItemResult.fail(BidQualityCheckItem.SCORING_RESPONSE,
                "技术方案缺失", "请核查", List.of(QualityEvidence.bid("技术方案缺陷", 2, quote)));
        assertThat(SemanticEvidenceBoundary.reconcile(fail, bid).status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void ungroundedScoringQuoteCannotRestoreFail() {
        var bid = new DocumentContent("bid", "hash", "技术方案完整",
                List.of(new DocumentContent.PageContent(2, "技术方案完整", List.of())), List.of());
        for (Integer page : java.util.Arrays.asList(null, 2, 99)) {
            var fail = QualityCheckItemResult.fail(BidQualityCheckItem.SCORING_RESPONSE,
                    "技术方案缺失", "请核查", List.of(QualityEvidence.bid("技术方案缺陷", page, "技术方案：____")));
            assertThat(SemanticEvidenceBoundary.reconcile(fail, bid).status())
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"本授权委托书须签字盖章后生效"})
    void signatureRequirementAlongsidePositiveObservationKeepsPass(String requirement) {
        String text = "【页面图像转写，非验签】法定代表人授权委托书\n法定代表人：张三 被授权人：李四 "
                + "授权事项：参与投标 授权期限：2026年1月1日至2026年12月31日\n"
                + requirement + "。\n图像可见公章。";
        var bid = new DocumentContent("bid", "hash", text,
                List.of(new DocumentContent.PageContent(1, text, List.of())),
                List.of(new com.xiyu.bid.compliance.domain.PageImage(
                        0, "image/png", "授权委托书", 1, 0, 0, "base64")));
        assertThat(AuthorizationFieldPolicy.hasSignedAuthorizationEvidence(bid)).isTrue();
        var pass = QualityCheckItemResult.pass(BidQualityCheckItem.AUTHORIZATION_LETTER, "要素齐全", List.of());
        assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).isEqualTo(QualityCheckStatus.PASS);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"签名：无"})
    void unrelatedAuthorizationStampCannotSignGrant(String defect) {
        String grant = "授权委托书\n法定代表人：张三\n受托人：李四\n授权范围：提交投标文件\n授权期限：长期有效\n" + defect;
        String stamp = "授权委托书\n【页面图像转写，非验签】图像可见公章";
        var bid = new DocumentContent("bid", "hash", grant + "\n" + stamp,
                List.of(new DocumentContent.PageContent(1, grant, List.of()),
                        new DocumentContent.PageContent(2, stamp, List.of())),
                List.of(new com.xiyu.bid.compliance.domain.PageImage(0, "image/png", "授权委托书", 2, 0, 0, "base64")));
        assertThat(AuthorizationFieldPolicy.assessAuthorizationFields(grant).filled()).isTrue();
        var pass = AuthorizationLetterCheckPolicy.check(bid);
        assertThat(pass.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(AuthorizationFieldPolicy.hasSignedAuthorizationEvidence(bid)).isFalse();
        assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(),
                TenderRequirements.empty(), java.util.Set.of()).stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.AUTHORIZATION_LETTER.getCode())).findFirst().orElseThrow();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"该证书为伪造证书"})
    void certificateAuthenticityContradictionCannotPass(String defect) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String text = "本公司具有ISO9001认证。" + defect + "。";
        var bid = DocumentContent.fromText("bid", "hash", text);
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证")).isFalse();
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req, List.of(QualityEvidence.fromKnowledgeMatch(match))).orElseThrow().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(QualityEvidence.fromKnowledgeMatch(match)));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match), req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode())).findFirst().orElseThrow();
            softly.assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"本公司具有ISO9001认证（证书编号：ABCD1234）但系借用"})
    void unknownCertificateStatusRequiresReviewDespiteMatchedKnowledge(String statement) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String text = "本公司具有ISO9001认证。" + statement + "。";
        var bid = DocumentContent.fromText("bid", "hash", text);
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证"))
                    .as("纯核心: %s", statement).isFalse();
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口: %s", statement)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界: %s", statement).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎: %s", statement).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        });
    }

    @Test
    void containingMandatoryConditionCannotResolveUnknownCertificateIdentity() {
        var req = new TenderRequirements(List.of(),
                List.of("投标人须具有施工资质证书", "具有施工资质证书"),
                List.of(), List.of(), List.of(), false);
        assertThat(req.isUnresolvedCertificateRequirement("具有施工资质证书")).isTrue();
        assertThat(req.isUnresolvedCertificateRequirement("投标人须具有施工资质证书")).isFalse();
    }

    @Test
    void unresolvedContainedCertificateDefectsCannotFailKnownBidderCondition() {
        var req = new TenderRequirements(List.of(),
                List.of("投标人须具有施工资质证书一级", "具有施工资质证书一级"),
                List.of(), List.of(), List.of(), false);
        for (var finding : List.of(QualificationDetail.Finding.EXPIRED,
                QualificationDetail.Finding.GRADE_INSUFFICIENT)) {
            var detail = new QualificationDetail("具有施工资质证书一级", "施工资质证书三级",
                    "EXPIRED", 1, "施工资质证书三级", -1, "待核实适用对象", finding);
            var result = QualificationDetailReconciler.reconcile(QualityCheckItemResult.pass(
                    BidQualityCheckItem.QUALIFICATION_MATCH, "匹配", List.of()), List.of(detail), req);
            assertThat(result.status()).as(finding.name()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            assertThat(result.details().getFirst().status()).isEqualTo("ATTENTION");
            var knownDetail = new QualificationDetail("投标人须具有施工资质证书一级", "施工资质证书三级",
                    "EXPIRED", 1, "施工资质证书三级", -1, "已确认强制条件", finding);
            var knownResult = QualificationDetailReconciler.reconcile(QualityCheckItemResult.pass(
                    BidQualityCheckItem.QUALIFICATION_MATCH, "匹配", List.of()), List.of(knownDetail), req);
            assertThat(knownResult.status()).as(finding.name()).isEqualTo(QualityCheckStatus.FAIL);
        }
    }

    @Test
    void combinedIsoMappingPreservesApplicabilityButExactIdentityTakesPriority() {
        var req = new TenderRequirements(List.of(),
                List.of("投标人须具有ISO9001、ISO14001认证", "具有ISO45001、ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        assertThat(req.isUnresolvedCertificateRequirement("ISO9001")).isFalse();
        assertThat(req.isUnresolvedCertificateRequirement("ISO14001")).isFalse();
        assertThat(req.isUnresolvedCertificateRequirement("ISO45001")).isTrue();
        var withExactUnknown = req.withMergedQualifications(List.of("ISO9001"));
        assertThat(withExactUnknown.isUnresolvedCertificateRequirement("ISO9001")).isTrue();
    }

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
    @DisplayName("无投标人上下文的独立产品或制造商证书子句，主体保持 UNKNOWN 避免盲目升级为投标人强制要求")
    void certificateClauseWithoutBidderContextRetainsTargetUnknown() {
        String clause = "具有ISO9001质量管理体系认证";
        var certs = CertificateRequirementPolicy.classify(clause, clause);
        assertThat(certs).hasSize(1);
        assertThat(certs.getFirst().target()).isEqualTo(QualificationCondition.Target.UNKNOWN);
    }

    @Test
    void certificateCategoryWithoutHolderContextRequiresManualConfirmation() {
        for (String clause : List.of("具有一级建造师资格证书", "具有施工资质证书")) {
            var certs = CertificateRequirementPolicy.classify(clause, clause);
            assertThat(certs).hasSize(1);
            assertThat(certs.getFirst().applicability()).isEqualTo(QualificationCondition.Applicability.MANDATORY);
            assertThat(certs.getFirst().target()).isEqualTo(QualificationCondition.Target.UNKNOWN);
            var tender = DocumentContent.fromText("tender.pdf", "hash", "资格要求\n" + clause);
            var req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tender);
            assertThat(req.hasUnresolvedCertificateRequirements()).isTrue();
            var detail = new QualificationDetail(clause, "", "UNMATCHED", 1, clause, null, "未匹配有效证书");
            var result = QualificationDetailReconciler.reconcile(
                    QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "匹配", List.of()),
                    List.of(detail), req);
            assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }

    @Test
    void unrelatedOrEmptyKnowledgeQuotesAreExcludedFromCertificateEvidence() {
        var tender = DocumentContent.fromText("tender.pdf", "hash", "投标人须具有ISO9001认证");
        var req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tender);
        var bid = DocumentContent.fromText("bid.pdf", "bid_hash", "ISO9001认证证明材料");
        for (String quote : java.util.Arrays.asList(null, "", "   ", "完全无关的营业执照")) {
            var evidence = new QualityEvidence("KNOWLEDGE_BASE", "资质记录", null, quote,
                    "unrelated", 1.0, KnowledgeMatch.SOURCE_QUALIFICATION, true);
            var result = QualificationVetoPolicy.checkFirstStage(bid, tender, req, List.of(evidence));
            assertThat(result).isPresent();
            assertThat(result.orElseThrow().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            assertThat(result.orElseThrow().evidences()).noneMatch(e -> "KNOWLEDGE_BASE".equals(e.sourceType()));
        }
    }

    @Test
    void genericKnowledgeQuotesCannotIdentifyRequiredCertificate() {
        for (String certificate : List.of("ISO9001认证", "增值电信业务经营许可证")) {
            var tender = DocumentContent.fromText("tender.pdf", "hash", "投标人须具有" + certificate);
            var req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tender);
            var bid = DocumentContent.fromText("bid.pdf", "bid_hash", certificate + "证明材料");
            for (String quote : List.of("认证", "许可证", "经营许可证", "管理体系认证", "企业资质证书")) {
                var evidence = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                        KnowledgeMatch.SOURCE_QUALIFICATION, "generic", quote, true));
                var result = QualificationVetoPolicy.checkFirstStage(bid, tender, req, List.of(evidence));
                assertThat(result).isPresent();
                assertThat(result.orElseThrow().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
                assertThat(result.orElseThrow().evidences())
                        .as("要求 %s 不得归属泛化引文 %s", certificate, quote)
                        .noneMatch(e -> "KNOWLEDGE_BASE".equals(e.sourceType()));
            }
        }
    }

    @Test
    void shortCertificateCodesCannotIdentifyRequiredCertificate() {
        for (String certificate : List.of("ISO9001认证", "ICP许可证", "EDI许可证")) {
            var tender = DocumentContent.fromText("tender.pdf", "hash", "投标人须具有" + certificate);
            var req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tender);
            var bid = DocumentContent.fromText("bid.pdf", "bid_hash", certificate + "证明材料");
            for (String quote : List.of("ISO", "ISO900", "IC", "ED")) {
                var evidence = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                        KnowledgeMatch.SOURCE_QUALIFICATION, "short", quote, true));
                var result = QualificationVetoPolicy.checkFirstStage(bid, tender, req, List.of(evidence)).orElseThrow();
                assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
                assertThat(result.evidences()).noneMatch(e -> "KNOWLEDGE_BASE".equals(e.sourceType()));
            }
            var exact = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                    KnowledgeMatch.SOURCE_QUALIFICATION, "exact", certificate, true));
            assertThat(QualificationVetoPolicy.checkFirstStage(bid, tender, req, List.of(exact)).orElseThrow().evidences())
                    .anyMatch(e -> "KNOWLEDGE_BASE".equals(e.sourceType()));
        }
    }

    @Test
    void certificateEvidenceMustCoverEveryRequiredIdentityCode() {
        for (var pair : List.of(
                List.of("ISO9001质量管理体系认证", "质量管理体系认证"),
                List.of("EDI和ICP许可证", "EDI许可证"),
                List.of("EDI和ICP许可证", "ICP许可证"),
                List.of("中华人民共和国增值电信业务经营许可证（EDI和ICP）",
                        "中华人民共和国增值电信业务经营许可证 国家级"))) {
            String certificate = pair.getFirst();
            var tender = DocumentContent.fromText("tender.pdf", "hash", "投标人须具有" + certificate);
            var req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tender);
            var bid = DocumentContent.fromText("bid.pdf", "bid_hash", certificate + "证明材料");
            var incomplete = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                    KnowledgeMatch.SOURCE_QUALIFICATION, "incomplete", pair.get(1), true));
            var result = QualificationVetoPolicy.checkFirstStage(bid, tender, req, List.of(incomplete)).orElseThrow();
            assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            assertThat(result.evidences()).as("要求 %s 不得归属引文 %s", certificate, pair.get(1))
                    .noneMatch(e -> "KNOWLEDGE_BASE".equals(e.sourceType()));
            var complete = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                    KnowledgeMatch.SOURCE_QUALIFICATION, "complete", certificate, true));
            assertThat(QualificationVetoPolicy.checkFirstStage(bid, tender, req, List.of(complete))
                    .orElseThrow().evidences()).contains(complete);
        }
    }

    @Test
    void certificateWithoutEnglishCodeRetainsEvidenceButRequiresBidProof() {
        String certificate = "建筑工程施工总承包资质证书";
        var tender = DocumentContent.fromText("tender.pdf", "hash", "投标人须具有" + certificate);
        var req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tender);
        var bid = DocumentContent.fromText("bid.pdf", "bid_hash", certificate + "证明材料");
        var evidence = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                KnowledgeMatch.SOURCE_QUALIFICATION, "chinese", certificate, true));
        var result = QualificationVetoPolicy.checkFirstStage(bid, tender, req, List.of(evidence)).orElseThrow();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.evidences()).contains(evidence);
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
    @DisplayName("招标文件表格中含 <br> HTML标签（如 EDI和<br>ICP）时，自动清洗标签，一票否决实证 PASS，资质匹配保留主体待确认且备注无英文术语")
    void htmlBrTagInTenderRequirementIsSanitizedAndProducesHumanizedRemark() {
        String bidPage = """
                第二章 投标人资格条件相关证明文件
                四、西域具有有效的中华人民共和国增值电信业务经营许可证（EDI和ICP）
                证书编号：合字B2-20210088
                有效期限：自2021年3月15日至2026年3月14日
                发证机关：中华人民共和国工业和信息化部
                """;
        DocumentContent bidDoc = new DocumentContent("bid.docx", "bid_hash", bidPage,
                List.of(new DocumentContent.PageContent(2, bidPage, List.of())));
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "tender_hash",
                "七、 投标人资格要求\n| 1 | 资格要求 | 具有有效的中华人民共和国增值电信业务经营许可证（EDI和<br>ICP） |");
        TenderRequirements req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tenderDoc);

        // 验证要求文字已清洗掉 <br>
        assertThat(req.certificateRequirements()).hasSize(1);
        assertThat(req.certificateRequirements().getFirst())
                .doesNotContain("<br>")
                .doesNotContain("<")
                .doesNotContain(">");

        // 一票否决闭环 PASS
        Optional<QualityCheckItemResult> vetoResult = QualificationVetoPolicy.checkFirstStage(bidDoc, tenderDoc, req, List.of());
        assertThat(vetoResult).isPresent();
        assertThat(vetoResult.orElseThrow().status()).isEqualTo(QualityCheckStatus.PASS);

        // 资质比对详情不含英文术语与 [UNKNOWN]
        var detail = new QualificationDetail(req.certificateRequirements().getFirst(),
                "中华人民共和国增值电信业务经营许可证 国家级", "MATCHED", 2, "第 2 页", 1236,
                "[UNKNOWN] 原检索状态=MATCHED；已匹配企业资质库有效证书「中华人民共和国增值电信业务经营许可证 国家级」（有效期至 2030-03-28）");
        var reconciled = QualificationDetailReconciler.reconcile(
                QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "资质匹配", List.of()),
                List.of(detail), req, bidDoc);

        assertThat(req.hasUnresolvedCertificateRequirements()).isTrue();
        assertThat(reconciled.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(reconciled.details()).hasSize(1);
        String remark = reconciled.details().getFirst().remark();
        assertThat(remark).doesNotContain("[UNKNOWN]");
        assertThat(remark).doesNotContain("MATCHED");
        assertThat(remark).doesNotContain("原检索状态=");
        assertThat(remark).contains("已匹配企业资质库有效证书");
    }

    @Test
    @DisplayName("无投标人主体的持证描述即使匹配资质库仍需人工确认")
    void certificateNameWithoutBidderRequiresConfirmation() {
        String p1 = "封面\n项目编号：XY-ZB-2026-0888\n营业执照有效期限：长期";
        String p2 = "第二章 投标人资格条件相关证明文件\n依法取得中华人民共和国增值电信业务经营许可证（EDI和ICP）。";
        DocumentContent bidDoc = new DocumentContent("bid.pdf", "bid_hash", p1 + "\n" + p2,
                List.of(
                        new DocumentContent.PageContent(1, p1, List.of()),
                        new DocumentContent.PageContent(2, p2, List.of())
                ));
        DocumentContent tenderDoc = DocumentContent.fromText("tender.pdf", "tender_hash",
                "七、 投标人资格要求\n投标人须具有中华人民共和国增值电信业务经营许可证（EDI和ICP）。");
        TenderRequirements req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tenderDoc);
        var kbEvidence = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                KnowledgeMatch.SOURCE_QUALIFICATION, "46", "中华人民共和国增值电信业务经营许可证（EDI和ICP） 国家级", true));

        Optional<QualityCheckItemResult> result = QualificationVetoPolicy.checkFirstStage(bidDoc, tenderDoc, req, List.of(kbEvidence));
        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.get().evidences()).anyMatch(e -> "KNOWLEDGE_BASE".equals(e.sourceType())
                && "中华人民共和国增值电信业务经营许可证（EDI和ICP） 国家级".equals(e.quote()));
        assertThat(SemanticEvidenceBoundary.reconcile(result.get(), bidDoc).status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
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
    void projectNumberCannotSupplyCertificateFactsOnSamePage() {
        var tender = DocumentContent.fromText("tender", "hash", "投标人须具有ISO9001认证");
        var req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tender);
        for (String text : List.of(
                "ISO9001认证证明材料\n项目编号：XY-ZB-2026-0888\n营业执照有效期限：长期",
                "ISO9001认证证明材料\n项目编号为XY-ZB-2026-0888\n有效期限：长期",
                "ISO9001认证证明材料\n档案编号：DOC2026\n有效期限：长期")) {
            var bid = DocumentContent.fromText("bid", "hash", text);
            assertThat(SemanticEvidenceBoundary.hasSubstantiveCertificateFacts(text)).as(text).isFalse();
            assertThat(QualificationVetoPolicy.checkFirstStage(bid, tender, req, List.of())
                    .orElseThrow().status()).as("入口: %s", text).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of());
            assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).as("最终边界: %s", text)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
        assertThat(SemanticEvidenceBoundary.hasSubstantiveCertificateFacts(
                "ISO9001认证\n证书编号为CN12345\n有效期限：长期")).isTrue();
    }

    @Test
    @DisplayName("身份证正反面要素经OCR完整识别后，委托人身份证项可闭环 PASS")
    void completeBothSidesIdFactsPassIdAuthLetterAndBoundary() {
        String idPage = """
                【页面图像转写，非验签】
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

    @Test
    @DisplayName("资质库有效且标书仅声明证书名称时仍须人工复核")
    void validMatchedNameReferencesCannotUpgradeManualReview() {
        var raw = QualityCheckItemResult.manualReview(BidQualityCheckItem.QUALIFICATION_MATCH,
                "初始人工复核", "建议复核", List.of());
        String name = "中华人民共和国增值电信业务经营许可证";
        var certificate = new com.xiyu.bid.entity.Qualification();
        certificate.setName(name);
        certificate.setExpiryDate(LocalDate.of(2030, 3, 28));
        var bid = DocumentContent.fromText("bid.pdf", "hash", "我司具有" + name + "。");
        var req = new TenderRequirements(List.of(), List.of("投标人须具有" + name),
                List.of(), List.of(), List.of(), false);
        var details = QualificationMatchPolicy.buildDetails(req.certificateRequirements(),
                List.of(certificate), bid, LocalDate.of(2026, 11, 8));
        assertThat(details).hasSize(1);
        assertThat(details.getFirst().finding()).isEqualTo(QualificationDetail.Finding.MATCHED_REFERENCE);
        assertThat(details.getFirst().bidPageNumber()).isNotNull();
        assertThat(QualificationMatchPolicy.checkFirstStage(bid, req)).isEmpty();
        var reconciled = QualificationDetailReconciler.reconcile(raw, details, req, bid);
        assertThat(reconciled.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(reconciled.details()).hasSize(1);
    }

    @Test
    void missingDepositTermsCannotBecomeStructuredExemption() {
        String context = "本项目采用综合评估法，投标人应按招标文件编制商务技术经济文件。".repeat(6);
        for (String clause : List.of("", "投标人须提交银行保函，金额及格式见附件。",
                "投标人应提交电子保函，具体要求见附件。", "投标人应提供担保，详见附件。")) {
            var requirements = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(),
                    DocumentContent.fromText("tender", "hash", context + clause));
            assertThat(requirements.depositRequirement()).as(clause).isNull();
        }
        var explicit = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(),
                DocumentContent.fromText("tender", "hash", "本项目免收投标保证金。"));
        assertThat(explicit.depositRequirement().required()).isFalse();
    }

    @Test
    void matchedDetailsCannotOverrideUnresolvedApplicabilityOrPlaceholderFailure() {
        String clause = "具有安全生产许可证";
        var requirements = new TenderRequirements(List.of(), List.of(clause), List.of(), List.of(), List.of(), false);
        assertThat(requirements.hasUnresolvedCertificateRequirements()).isTrue();
        var detail = new QualificationDetail(clause, "安全生产许可证", "MATCHED", 1,
                "我司具有安全生产许可证", 365, "资料库匹配", QualificationDetail.Finding.MATCHED_REFERENCE);
        var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "匹配", List.of())
                .withDetails(List.of(detail));
        var bid = DocumentContent.fromText("bid", "hash", "我司具有安全生产许可证");
        assertThat(SemanticEvidenceBoundary.reconcileQualificationApplicability(pass, bid, requirements).status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        var known = new TenderRequirements(List.of(), List.of("投标人须具有安全生产许可证"),
                List.of(), List.of(), List.of(), false);
        var blank = DocumentContent.fromText("bid", "hash", "安全生产许可证\n证书编号：_____");
        assertThat(QualificationMatchPolicy.checkFirstStage(blank, known)).hasValueSatisfying(
                r -> assertThat(r.status()).isEqualTo(QualityCheckStatus.FAIL));
        var results = BidDocumentQualityEngine.evaluateAll(blank, DocumentContent.empty(), List.of(), known, java.util.Set.of());
        assertThat(results).filteredOn(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_MATCH.getCode()))
                .singleElement().satisfies(r -> assertThat(r.status()).isEqualTo(QualityCheckStatus.FAIL));
    }

    @Test
    @DisplayName("未传入适用性事实时，免收结论文案不能绕过视觉核验")
    void exemptionConclusionAloneCannotBypassVision() {
        var passResult = QualityCheckItemResult.pass(BidQualityCheckItem.DEPOSIT_RECEIPT,
                "【不涉及】招标文件未要求缴纳投标保证金/响应担保（免收/不涉及）", List.of());
        var vision = DepositReceiptVisionResult.unavailable("无回单图片");
        var fused = DepositReceiptVisionFusionPolicy.fuseDepositReceipt(passResult, vision);
        assertThat(fused.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void negatedOrQuotedExemptionsCannotBypassRequiredDepositVision() {
        var tender = DocumentContent.fromText("tender.pdf", "hash", "本项目要求缴纳投标保证金10000元。");
        var req = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tender);
        assertThat(req.depositRequirement().required()).isTrue();
        for (String conclusion : List.of("本项目不适用免收政策，已提供保证金回单",
                "引用免收说明：其他项目不要求缴纳，本项目已缴纳保证金",
                "本项目要求缴纳保证金，已提供回单；不涉及其他款项")) {
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.DEPOSIT_RECEIPT, conclusion, List.of());
            for (var vision : List.of(DepositReceiptVisionResult.unavailable("无图像"),
                    DepositReceiptVisionResult.available(List.of(), "vision-v1"))) {
                assertThat(DepositReceiptVisionFusionPolicy.fuseDepositReceipt(pass, vision).status())
                        .as(conclusion).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            }
        }
    }

    @Test
    @DisplayName("系统报价承诺不能替代提交事实核验")
    void systemDiscountWithBidderCommitmentRequiresSubmissionReview() {
        var tender = DocumentContent.fromText("tender.pdf", "thash",
                "第三卷 经济标：要求在系统报价表中填报折扣率。");
        var bid = DocumentContent.fromText("bid.docx", "bhash",
                "经济标响应说明：具体折扣率详见系统报价表，我司严格遵守报价规则。");
        var raw = QualityCheckItemResult.manualReview(BidQualityCheckItem.BID_OPENING_TABLE, "未包含纸面表", "复核", List.of());
        var req = new PriceRequirement(true, null, null, "系统折扣率报价", false, false, "系统报价要求");
        var result = PricingSubmissionPolicy.reconcile(raw, bid, tender, req);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("不能证明系统已完成填报");
    }

    @Test
    @DisplayName("授权字段不完整时模型通过不能覆盖人工核验")
    void authorizationLetterLlmPassCannotOverrideIncompleteFields() {
        var llmPass = QualityCheckItemResult.pass(BidQualityCheckItem.AUTHORIZATION_LETTER,
                "法定代表人授权委托书完整有效，被授权人丁思宇具有全权代表资格", List.of());
        var bid = DocumentContent.fromText("bid.docx", "bhash",
                "法定代表人授权委托书\n委托代理人：丁思宇\n全权代表本公司参加本次投标");
        var finalResult = AuthorizationLetterCheckPolicy.enforcePositiveContract(llmPass, bid, null);
        assertThat(finalResult.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void completeAuthorizationFieldsPreserveExpiryFailureAndEvidence() {
        var bid = DocumentContent.fromText("bid.docx", "hash",
                "授权委托书\n法定代表人：张三\n委托代理人：李四\n授权范围：提交投标文件\n授权期限：2025年1月1日至2025年12月31日");
        assertThat(AuthorizationLetterCheckPolicy.check(bid).status()).isEqualTo(QualityCheckStatus.PASS);
        var failure = QualityCheckItemResult.fail(BidQualityCheckItem.AUTHORIZATION_LETTER,
                "授权期限未覆盖本次投标日期", "请补充有效授权", List.of(
                        QualityEvidence.bid("授权期限", 1, "授权期限：2025年1月1日至2025年12月31日")));
        assertThat(AuthorizationLetterCheckPolicy.enforcePositiveContract(failure, bid, null)).isEqualTo(failure);
    }

    @Test
    void completeAuthorizationFieldsPreserveSignatureReviewAndEvidence() {
        var bid = DocumentContent.fromText("bid.docx", "hash",
                "授权委托书\n法定代表人：张三\n委托代理人：李四\n授权范围：提交投标文件\n授权期限：自签署日起至投标有效期结束");
        assertThat(AuthorizationLetterCheckPolicy.check(bid).status()).isEqualTo(QualityCheckStatus.PASS);
        var review = QualityCheckItemResult.manualReview(BidQualityCheckItem.AUTHORIZATION_LETTER,
                "授权签章有效性无法确认", "请核对原始签署页", List.of(
                        QualityEvidence.bid("授权签署页", 1, "法定代表人：张三")));
        assertThat(AuthorizationLetterCheckPolicy.enforcePositiveContract(review, bid, null)).isEqualTo(review);
    }

    @Test
    void mixedKnownAndUnresolvedCertificatesStayManual() {
        var req = new TenderRequirements(List.of(),
                List.of("投标人须具有ISO9001认证", "具有施工资质证书"),
                List.of(), List.of(), List.of(), false);
        assertThat(req.hasUnresolvedCertificateRequirements()).isTrue();
        var details = req.certificateRequirements().stream().map(r -> new QualificationDetail(
                r, r, "MATCHED", 1, r, 365, "已匹配", QualificationDetail.Finding.MATCHED_REFERENCE)).toList();
        var result = QualificationDetailReconciler.reconcile(QualityCheckItemResult.pass(
                BidQualityCheckItem.QUALIFICATION_MATCH, "匹配", List.of()), details, req);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.details()).anyMatch(d -> "ATTENTION".equals(d.status()));
    }

    @Test
    void systemPricingMentionCannotProveSubmission() {
        var tender = DocumentContent.fromText("tender.pdf", "hash", "要求在系统报价表中填报折扣率。");
        var req = new PriceRequirement(true, null, null, "系统折扣率报价", false, false, "系统报价要求");
        for (String text : List.of("系统报价尚未填报", "引用招标说明：要求在系统端填报折扣率", "系统报价与纸面报价冲突")) {
            var bid = DocumentContent.fromText("bid.pdf", "hash", text);
            var raw = QualityCheckItemResult.fail(BidQualityCheckItem.BID_OPENING_TABLE, "待核实报价", "核验", List.of());
            assertThat(PricingSubmissionPolicy.reconcile(raw, bid, tender, req).status())
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }

    @Test
    void guidelinesAcceptCompleteTranscriptionElements() {
        assertThat(AdjudicationGuidelines.allGuidelines()).contains("文字转写规则", "判定为 PASS")
                .doesNotContain("即认定已附证件凭证",
                        "即认定为已附证件", "即视为已提供有效主体资格证明");
    }

    @Test
    void matchedQualificationWithoutBidderRequiresConfirmation() {
        var bid = DocumentContent.fromText("bid.pdf", "hash", "具有增值电信业务经营许可证");
        var evidence = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                KnowledgeMatch.SOURCE_QUALIFICATION, "record", "增值电信业务经营许可证", true));
        var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO,
                "符合要求", List.of(evidence));
        assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void expiredCertificateInSameClauseCannotPreserveVetoPass() {
        var bid = DocumentContent.fromText("bid.pdf", "hash", "本公司具有增值电信业务经营许可证，但证书已过期。");
        var evidence = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                KnowledgeMatch.SOURCE_QUALIFICATION, "record", "增值电信业务经营许可证", true));
        var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO,
                "符合要求", List.of(evidence));
        assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void unrelatedStandardCodesElsewhereInBidDoNotDemoteMatchedVetoPass() {
        var p1 = "第二章 资格证明\n本公司具有增值电信业务经营许可证（EDI和ICP）。";
        var p2 = "第三章 技术参数\n产品符合GB/T19001及GB/T24001标准。";
        var bid = new DocumentContent("bid.pdf", "hash", p1 + "\n" + p2,
                List.of(new DocumentContent.PageContent(1, p1, List.of()),
                        new DocumentContent.PageContent(2, p2, List.of())));
        var evidence = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                KnowledgeMatch.SOURCE_QUALIFICATION, "record", "增值电信业务经营许可证（EDI和ICP）", true));
        var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO,
                "符合要求", List.of(evidence));
        assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                .isEqualTo(QualityCheckStatus.PASS);
    }

    @Test
    void qualificationTitlesTemplatesAndPartialFactsCannotProveAllApplicableRequirements() {
        for (String text : List.of(
                "目录\n营业执照 ........ 1\n审计报告 ........ 2\n承诺函 ........ 3\n",
                "附件清单：营业执照、审计报告、承诺函。",
                "营业执照：【统一社会信用代码】\n审计报告：【此处插入审计报告】\n承诺函：____",
                "营业执照\n统一社会信用代码：91310000123456789X\n法定代表人：张三\n审计报告见附件，承诺函已附。")) {
            var bid = DocumentContent.fromText("bid.pdf", "hash", text + "项目说明。".repeat(60));
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_REVIEW,
                    "符合资格要求", List.of());
            assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("目录、模板及缺少财务正文不能证明全部资格要求：%s", text)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }

    @Test
    void ordinaryBodyOrTenderEvidenceCannotProveCompositeQualification() {
        var bid = DocumentContent.fromText("bid.pdf", "hash", "本公司提供货物并承诺履约。");
        for (var evidence : List.of(
                QualityEvidence.bid("普通正文", 1, "本公司提供货物并承诺履约。"),
                new QualityEvidence("TENDER", "资格要求", 1, "须提供财务及社保证明", null, null, null, null))) {
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_REVIEW,
                    "资格评审通过", List.of(evidence));
            assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }

    @Test
    void genericSalutationPrefixCannotHideWrongRecipient() {
        var requirement = new BidLetterRequirement("投标函", "上海西域机电系统有限公司", null, null);
        for (var salutation : List.of(
                "致采购人：北京某某集团采购中心：",
                "致采购人：\n北京某某集团采购中心：",
                "致招标人：\r\n  北京某某集团采购中心：",
                "致采购人：\n\n北京某某集团采购中心",
                "致招标人：北京某某集团采购中心：",
                "致：北京某某集团采购中心：\n说明：致采购人表达承诺。",
                "致：北京某某集团采购中心：\n致采购人：",
                "致采购人或采购代理机构：北京某某集团采购中心：")) {
            var bid = DocumentContent.fromText("bid.pdf", "hash",
                    "投标函\n" + salutation + "\n我方承诺响应全部条款。\n投标人：某公司（盖章）");
            assertThat(BidLetterCheckPolicy.check(bid, requirement).status())
                    .as(salutation).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }

    @Test
    void genericSalutationsCannotHideRecipientsOfUnlistedOrganizationTypes() {
        var requirement = new BidLetterRequirement("投标函", "上海西域机电系统有限公司", null, null);
        for (String recipient : List.of("北京某某大学", "北京某某学校", "北京某某银行", "北京某某指挥部",
                "北京某某工作组", "某未分类收件主体")) {
            for (String separator : List.of("\n", "\r\n  ", "\n\n")) {
                for (String ending : List.of("：", "")) {
                    var bid = DocumentContent.fromText("bid.pdf", "hash",
                            "投标函\n致采购人：" + separator + recipient + ending
                                    + "\n我方承诺响应全部条款。\n投标人：某公司（盖章）");
                    assertThat(BidLetterCheckPolicy.check(bid, requirement).status())
                            .as("%s/%s/%s", recipient, separator, ending)
                            .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
                }
            }
        }
    }

    @Test
    void completeGenericSalutationsRemainAccepted() {
        var requirement = new BidLetterRequirement("投标函", "上海西域机电系统有限公司", null, null);
        for (var salutation : List.of("致采购人：", "致招标人:", "致采购人或采购代理机构：",
                "致采购人或招标代理机构：", "致招标人或招标代理机构：")) {
            for (String separator : List.of("\n", "\r\n  ", "\n\n")) {
                var bid = DocumentContent.fromText("bid.pdf", "hash",
                        "投标函\n" + salutation + separator + "我方承诺响应全部条款。\n投标人：某公司（盖章）");
                assertThat(BidLetterCheckPolicy.check(bid, requirement).status())
                        .as("%s/%s", salutation, separator).isEqualTo(QualityCheckStatus.PASS);
            }
        }
    }

    @Test
    void truncatedHardRequirementsMustRemainIncomplete() {
        for (String json : List.of(
                "{\"mandatoryQualifications\":[\"营业执照\",\"社保",
                "{\"mandatoryQualifications\":[\"营业执照\"],\"vetoClauses\":[{\"clause\":\"不得转包\",\"keywords\":[]},{\"clause\":\"不得")) {
            assertThat(TenderRequirementsParser.parse(json).missing()).as(json).isTrue();
        }
    }

    @Test
    void longQualificationMaterialListsCannotProveCompliance() {
        for (String text : List.of(
                "目录\n营业执照 ........ 1\n审计报告 ........ 2\n无重大违法承诺 ........ 3\n",
                "材料清单：营业执照、审计报告、无重大违法声明。")) {
            var bid = DocumentContent.fromText("bid.pdf", "hash", text + "项目说明。".repeat(60));
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_REVIEW,
                    "资格符合要求", List.of());
            assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }

    @Test
    void unrelatedOrPartialKnowledgeMatchesCannotProveVetoCompliance() {
        var bid = DocumentContent.fromText("bid.pdf", "hash",
                "本次要求ISO9001及ISO27001证书，现仅声明具有ISO9001。证明页待补。");
        for (String quote : List.of("无关的建筑施工资质", "ISO9001")) {
            var evidence = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                    KnowledgeMatch.SOURCE_QUALIFICATION, "record", quote, true));
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO,
                    "全部资格符合要求", List.of(evidence));
            assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as(quote).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }

    @Test
    void kbRequiresBidderPositiveCertificateStatement() {
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        var evidence = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true));
        for (String text : List.of("采购要求：具有ISO9001认证", "合作方具有ISO9001认证", "其他投标人具有ISO9001认证",
                "非本公司具有ISO9001认证", "非我司持有ISO9001认证",
                "本公司尚未获得ISO9001认证", "本公司不持有ISO9001认证",
                "采购要求：本公司须具有ISO9001认证",
                "本公司具有ISO9001认证，但已过期。", "本公司具有ISO9001认证，该证书已吊销。",
                "本公司具有ISO9001认证。该证书已过期。", "本公司具有ISO9001认证。该认证已吊销。")) {
            var bid = DocumentContent.fromText("bid", "hash", text);
            assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as(text).isNotEqualTo(QualityCheckStatus.PASS);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as(text).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }

    @Test
    void thirdPartyCertificateMaterialsCannotProveBidderCertification() {
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        var evidence = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true));
        for (String text : List.of("本公司持有合作企业的ISO9001认证复印件。",
                "我司持有供应商的ISO9001认证证书。",
                "本投标人获得关联公司的ISO9001认证扫描件。",
                "本公司持有ISO9001认证证书复印件（属于供应商）。",
                "本公司持有ISO9001认证证书（持证人为关联公司）。",
                "本公司持有ISO9001认证证书，为供应商所有。")) {
            var bid = DocumentContent.fromText("bid", "hash", text);
            var result = QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow();
            assertThat(result.status()).as(text).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            assertThat(SemanticEvidenceBoundary.reconcile(result, bid).status()).as(text)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var proposedPass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO,
                    "通过", List.of(evidence));
            assertThat(SemanticEvidenceBoundary.reconcile(proposedPass, bid).status()).as(text)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }

    @Test
    void compositeCertificateSingleDefectBlocksKbPass() {
        var req = new TenderRequirements(List.of(), List.of("增值电信业务经营许可证（EDI和ICP）"),
                List.of(), List.of(), List.of(), false);
        var evidence = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                KnowledgeMatch.SOURCE_QUALIFICATION, "record", "增值电信业务经营许可证（EDI和ICP）", true));
        var text = "本公司具有增值电信业务经营许可证（EDI和ICP）。本公司未取得ICP许可证。";
        var bid = DocumentContent.fromText("bid", "hash", text);
        assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                List.of(evidence)).orElseThrow().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO,
                "通过", List.of(evidence));
        assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void contradictoryCertificatePageBlocksKbPassRegardlessOfPageOrder() {
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        var evidence = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true));
        for (var pages : List.of(
                List.of("本公司具有ISO9001认证", "本公司ISO9001认证已过期"),
                List.of("本公司ISO9001认证已过期", "本公司具有ISO9001认证"))) {
            var bid = new DocumentContent("bid", "hash", String.join("\n", pages),
                    List.of(new DocumentContent.PageContent(1, pages.get(0), List.of()),
                            new DocumentContent.PageContent(2, pages.get(1), List.of())));
            assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).isNotEqualTo(QualityCheckStatus.PASS);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO,
                    "通过", List.of(evidence));
            assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }

    @Test
    void engineRequiresIdImagesAndCompleteBothSidesTranscription() {
        String complete = "【页面图像转写，非验签】身份证 姓名：张伟 性别：男 "
                + "公民身份号码：310115198805121234 签发机关：上海市公安局 "
                + "有效期限：2020.05.12-2040.05.12";
        String partial = "【页面图像转写，非验签】身份证 姓名：张伟 "
                + "公民身份号码：310115198805121234 有效期限：2020.05.12-2040.05.12";
        for (boolean hasImage : List.of(false, true)) {
            for (String text : List.of(complete, partial)) {
                var bid = new DocumentContent("bid", "hash", text,
                        List.of(new DocumentContent.PageContent(1, text, List.of())),
                        hasImage ? List.of(new com.xiyu.bid.compliance.domain.PageImage(
                                0, "image/png", "身份证", 1, 0, 0, "base64")) : List.of());
                var expected = hasImage && text.equals(complete)
                        ? QualityCheckStatus.PASS : QualityCheckStatus.MANUAL_REVIEW;
                var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(),
                        List.of(), TenderRequirements.empty(), java.util.Set.of()).stream()
                        .filter(r -> r.itemCode().equals(BidQualityCheckItem.ID_AUTH_LETTER.getCode()))
                        .findFirst().orElseThrow();
                assertThat(result.status()).as("hasImage=%s, text=%s", hasImage, text).isEqualTo(expected);
                var proposedPass = QualityCheckItemResult.pass(BidQualityCheckItem.ID_AUTH_LETTER,
                        "转写齐全", List.of());
                assertThat(SemanticEvidenceBoundary.reconcile(proposedPass, bid).status()).isEqualTo(expected);
            }
        }
    }

    @Test
    void unrelatedImageCannotProveIdTranscriptionOnAnotherPage() {
        String text = "【页面图像转写，非验签】身份证 姓名：张伟 性别：男 "
                + "公民身份号码：310115198805121234 签发机关：上海市公安局 "
                + "有效期限：2020.05.12-2040.05.12";
        for (Integer imagePage : java.util.Arrays.asList(1, null, 2)) {
            var bid = new DocumentContent("bid", "hash", text,
                    List.of(new DocumentContent.PageContent(1, "封面Logo", List.of()),
                            new DocumentContent.PageContent(2, text, List.of())),
                    List.of(new com.xiyu.bid.compliance.domain.PageImage(
                            0, "image/png", "身份证", imagePage, 0, 0, "base64")));
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.ID_AUTH_LETTER, "齐全", List.of());
            assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).as("imagePage=%s", imagePage)
                    .isEqualTo(Integer.valueOf(2).equals(imagePage)
                            ? QualityCheckStatus.PASS : QualityCheckStatus.MANUAL_REVIEW);
        }
    }

    @Test
    void unsignedAuthorizationTranscriptionCannotKeepProposedPass() {
        var bid = DocumentContent.fromText("bid", "hash",
                "【页面图像转写，非验签】法定代表人授权委托书\n授权人：张三 被授权人：李四 "
                        + "授权事项：参与投标 授权期限：2026年1月1日至2026年12月31日");
        var pass = QualityCheckItemResult.pass(BidQualityCheckItem.AUTHORIZATION_LETTER, "字段齐全", List.of());
        assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(AdjudicationGuidelines.allGuidelines())
                .contains("无法确认签字盖章时，必须判定为 MANUAL_REVIEW");
    }

    @Test
    void signedAuthorizationTranscriptionWithImageKeepsProposedPass() {
        String text = "【页面图像转写，非验签】法定代表人授权委托书\n授权人：张三 被授权人：李四 "
                + "授权事项：参与投标 授权期限：2026年1月1日至2026年12月31日\n法定代表人（签字）：张三 委托代理人（签字）：李四\n投标人：（盖章）";
        var bid = new DocumentContent("bid", "hash", text,
                List.of(new DocumentContent.PageContent(1, text, List.of())),
                List.of(new com.xiyu.bid.compliance.domain.PageImage(
                        0, "image/png", "授权委托书", 1, 0, 0, "base64")));
        var pass = QualityCheckItemResult.pass(BidQualityCheckItem.AUTHORIZATION_LETTER, "要素齐全", List.of());
        assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                .isEqualTo(QualityCheckStatus.PASS);
        assertThat(AdjudicationGuidelines.allGuidelines())
                .contains("最终法律盖章效力由签字盖章规范性专项核查覆盖");
    }


}
