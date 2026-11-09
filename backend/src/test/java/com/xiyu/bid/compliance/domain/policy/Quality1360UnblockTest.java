// Input: 1360 原件真实场景与边界用例（第一刀+第二刀）
// Output: 验证资格条件身份隔离与授权风险保护（含空值签署栏、后置否定与肯定观察冲突）、评分具体缺陷保留与样例降级及普通章节恢复正文、签署要求与观察并存、响应性差异表、否决警示语、文件组成、保证金图片OCR闭环及裸状态同页与跨页证书缺陷等价保护
// Pos: compliance/domain/policy/ - 1360 标书核查解除硬编码阻塞纯核心测试 (FP-Java)
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualificationDetail;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import com.xiyu.bid.compliance.domain.QualityEvidence;
import com.xiyu.bid.compliance.service.LlmVerdictParser;
import com.xiyu.bid.compliance.service.TenderRequirementsParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class Quality1360UnblockTest {

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"项目经理业绩", "项目经理业绩证明", "项目管理机构配置"})
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
    @org.junit.jupiter.params.provider.ValueSource(strings = {"项目经理业绩证明", "项目管理机构配置"})
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
    @org.junit.jupiter.params.provider.ValueSource(strings = {"本公司未取得ISO9001认证。", "本公司具有ISO9001认证。该证书已过期。", "已被吊销。"})
    void certificateSampleDefectsCannotContaminateFormalStatement(String sample) {
        for (String separator : List.of("\n", "\n\f\n")) {
            String text = "填写示例：\n" + sample + separator + "正式声明：\n本公司具有ISO9001认证。";
            assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证")).isTrue();
            String priorStatement = "本公司具有ISO9001认证。\n填写示例：\n" + sample
                    + separator + "正式正文：\n该证书有效。";
            assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(priorStatement, "ISO9001认证"))
                    .as("样例不能改变先前正式证书身份").isTrue();
            assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(
                    text + "该证书已过期。", "ISO9001认证")).isFalse();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "## 合同样例", "### 合同条款（样本）", "合同条款（样本）", "延保服务协议书（样本）"})
    void formattedBusinessSampleHeadingsOwnSamePageAndFollowingPageBlanks(String heading) {
        for (boolean paginated : List.of(false, true)) {
            for (boolean formalBody : List.of(false, true)) {
                String quote = "技术方案：____";
                String body = (formalBody ? "## 正式正文：\n" : "") + quote;
                String text = heading + "\n" + body;
                var pages = paginated
                        ? List.of(new DocumentContent.PageContent(2, heading, List.of()),
                                new DocumentContent.PageContent(3, body, List.of()))
                        : List.of(new DocumentContent.PageContent(2, text, List.of()));
                var bid = new DocumentContent("bid", "hash", text, pages, List.of());
                var fail = QualityCheckItemResult.fail(BidQualityCheckItem.SCORING_RESPONSE,
                        "技术方案存在占位", "请核实",
                        List.of(QualityEvidence.bid("缺陷", paginated ? 3 : 2, quote)));
                assertThat(SemanticEvidenceBoundary.reconcile(fail, bid).status())
                        .as("标题=%s，跨页=%s，正式正文=%s", heading, paginated, formalBody)
                        .isEqualTo(formalBody ? QualityCheckStatus.FAIL : QualityCheckStatus.MANUAL_REVIEW);
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "技术方案：（详见附件一）", "实施方案：[见第30页]", "本方案（技术方案）已完整提供",
            "技术方案：（说明详见附件一）", "技术方案：[请参见第30页]"})
    void filledReferencesAndParenthesesCannotProveScoringDefect(String text) {
        var bid = new DocumentContent("bid", "hash", text,
                List.of(new DocumentContent.PageContent(3, text, List.of())), List.of());
        var fail = QualityCheckItemResult.fail(BidQualityCheckItem.SCORING_RESPONSE,
                "技术方案存在占位", "请核实", List.of(QualityEvidence.bid("缺陷", 3, text)));
        assertThat(SemanticEvidenceBoundary.reconcile(fail, bid).status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void scoringExampleScopeSurvivesPaginationAndEndsAtFormalBody(boolean formalBody) {
        String text = (formalBody ? "正式正文：\n" : "") + "技术方案：____";
        var bid = new DocumentContent("bid", "hash", "合同样例（仅供参考）\n" + text,
                List.of(new DocumentContent.PageContent(2, "合同样例（仅供参考）", List.of()),
                        new DocumentContent.PageContent(3, text, List.of())), List.of());
        var fail = QualityCheckItemResult.fail(BidQualityCheckItem.SCORING_RESPONSE,
                "技术方案存在占位", "请核实", List.of(QualityEvidence.bid("缺陷", 3, "技术方案：____")));
        assertThat(SemanticEvidenceBoundary.reconcile(fail, bid).status())
                .isEqualTo(formalBody ? QualityCheckStatus.FAIL : QualityCheckStatus.MANUAL_REVIEW);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "本次必填技术方案：____", "我司本次未提供技术方案及实施组织设计正文",
            "技术方案：【请填写技术方案】", "技术方案：[待填]",
            "技术方案：（请在此处输入实施方案）", "【请填写技术方案】",
            "合同样例（仅供参考）\n技术方案：____\n正式正文：\n【请填写技术方案】"})
    void scoringFailWithLocatedConcreteDefectIsPreserved(String quote) {
        var bid = new DocumentContent("bid", "hash", quote,
                List.of(new DocumentContent.PageContent(2, quote, List.of())), List.of());
        var fail = QualityCheckItemResult.fail(BidQualityCheckItem.SCORING_RESPONSE,
                "本次必填技术方案存在占位或核心内容缺失", "请补齐技术方案",
                List.of(QualityEvidence.bid("技术方案缺陷", 2, quote)));
        assertThat(SemanticEvidenceBoundary.reconcile(fail, bid)).isEqualTo(fail);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "合同样例\n技术方案：____", "参考格式\n技术方案：____", "技术方案已完整响应"})
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
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "我司将按甲方要求提供售后服务。", "乙方服务联系人为张三。",
            "参考资料列于附件。", "请填写售后联系人。", "合同样例另见附件。",
            "合同条款（样本）另见附件。", "## 技术方案"})
    void unrelatedPageTextCannotEraseLocatedScoringDefect(String unrelated) {
        for (String quote : List.of("本次必填技术方案：____", "我司本次未提供技术方案正文")) {
            for (String text : List.of(quote + "\n" + unrelated, unrelated + "\n" + quote,
                    quote + "。" + unrelated)) {
                var bid = new DocumentContent("bid", "hash", text,
                        List.of(new DocumentContent.PageContent(2, text, List.of())), List.of());
                var fail = QualityCheckItemResult.fail(BidQualityCheckItem.SCORING_RESPONSE,
                        "技术方案缺失", "请补齐", List.of(QualityEvidence.bid("技术方案缺陷", 2, quote)));
                assertThat(SemanticEvidenceBoundary.reconcile(fail, bid)).as(text).isEqualTo(fail);
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "合同样例\n技术方案：____", "参考格式\n技术方案：____",
            "填写示例：技术方案：____", "合同模板\n我司本次未提供技术方案正文",
            "合同样例（仅供参考）\n技术方案：____", "1. 填写示例：\n技术方案：____",
            "第三章 合同样本说明\n技术方案：____", "附录：格式样例（仅供参考）\n技术方案：____",
            "合同样例（仅供参考）\n技术方案：【请填写技术方案】"})
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
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "本授权委托书须签字盖章后生效", "须提供手写签名及公章印迹", "请签字并盖章"})
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
    @org.junit.jupiter.params.provider.ValueSource(strings = {"已过期", "已到期", "已失效", "已吊销", "已作废",
            "已被吊销", "现已被撤销", "实际已过期",
            "已被暂停", "已经被暂停", "被暂停", "现已被暂停", "经查已被暂停",
            "处于特殊处置状态", "系借用", "该证书已被暂停"})
    void bareCertificateDefectHasSameVerdictAcrossPages(String defect) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String first = "本公司具有ISO9001认证。";
        String second = defect + "。";
        for (boolean paginated : List.of(false, true)) {
            String text = first + (paginated ? "\n\f\n" : "") + second;
            var bid = new DocumentContent("bid", "hash", text,
                    paginated ? List.of(new DocumentContent.PageContent(1, first, List.of()),
                            new DocumentContent.PageContent(2, second, List.of()))
                            : List.of(new DocumentContent.PageContent(1, text, List.of())), List.of());
            org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证"))
                        .as("分页=%s，%s", paginated, defect).isFalse();
                softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                        List.of(evidence)).orElseThrow().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
                var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
                softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                        .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
                var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                        req, java.util.Set.of()).stream()
                        .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                        .findFirst().orElseThrow();
                softly.assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            });
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"签名：无", "签字：未提供", ""})
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
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "该证书为伪造证书", "该证书系假证", "该证书真实性无法确认",
            "该证书真实性存疑", "该证书真实性待核", "该证书真伪存疑", "该证书真伪不明", "该证书虚假", "该证书不实"})
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
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "本公司具有ISO9001认证（证书编号：ABCD1234）但系借用",
            "本公司具有ISO9001认证（有效期至2029年12月31日）但系借用",
            "该证书编号：ABCD1234但权属待议", "该证书有效期至2029年12月31日但处于特殊处置状态",
            "该证书系借用", "该证书为借来使用", "该认证通过租赁使用", "该资质权属待议",
            "该证书处于特殊处置状态", "ISO9001认证系借用", "该证书属于本公司但系借用",
            "该证书由认证机构颁发但系借用", "该证书未过期但系借用", "该证书已随本次投标文件一并提交但系借用",
            "该证书系借用。该证书属于本公司", "该证书由认证机构颁发。该证书系借用",
            "材料说明：以下为本次提交材料。该证书系借用", "\f该证书系借用",
            "\n正式正文：\n该证书已过期", "\n## 正式声明：\n该证书已被暂停",
            "\n填写示例：\n本公司具有ISO14001认证。该证书有效。\n正式正文：\n该证书已过期",
            "\n填写示例：\n已被吊销。\n\f\n正式正文：\n该证书系借用",
            "\n正式正文：\n已过期", "\n正式正文：\n\f\n该证书已过期"})
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("omittedCertificateStateCases")
    void omittedCertificateObjectUnknownStateCannotPassAcrossSentenceOrPage(String state, String separator) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String first = "本公司具有ISO9001认证";
        String text = first + separator + state + "。";
        var bid = separator.contains("\f")
                ? new DocumentContent("bid", "hash", text, List.of(
                        new DocumentContent.PageContent(1, first + "。", List.of()),
                        new DocumentContent.PageContent(2, state + "。", List.of())), List.of())
                : DocumentContent.fromText("bid", "hash", text);
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证"))
                    .as("纯核心: %s / %s", state, separator).isFalse();
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口: %s / %s", state, separator)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界: %s / %s", state, separator).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎: %s / %s", state, separator)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        });
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> omittedCertificateStateCases() {
        return java.util.stream.Stream.of("权属待议", "状态待确认", "权属尚待核实", "状态有待核验",
                        "仅为借用", "实际为借用", "只是借用", "实为借用",
                        "实际借用自合作方", "借用自合作方", "实际借用", "向合作方借用", "实际租用自合作方", "通过租赁使用")
                .flatMap(state -> java.util.stream.Stream.of("，", "。", "。\n", "。\n\f\n")
                        .map(separator -> org.junit.jupiter.params.provider.Arguments.of(state, separator)));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"有效", "有效的", "本公司的", "我司的", "我方的"})
    void knownCertificateObjectPrefixesPreserveMatchedPass(String prefix) {
        String text = "本公司具有" + prefix + "ISO9001认证。";
        var bid = DocumentContent.fromText("bid", "hash", text);
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证")).isTrue();
        assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                List.of(QualityEvidence.fromKnowledgeMatch(match))).orElseThrow().status()).isEqualTo(QualityCheckStatus.PASS);
        var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                req, java.util.Set.of()).stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode())).findFirst().orElseThrow();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
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
    void headinglessHtmlRequirementIsCleanedBeforeFusionAndMatching() {
        String raw = "具有EDI和<br>ICP许可证";
        var tender = DocumentContent.fromText("tender.pdf", "hash", raw);
        var requirements = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), tender);

        assertThat(requirements.certificateRequirements()).containsExactly("具有EDI和ICP许可证");
        assertThat(requirements.qualificationConditions().getFirst().source().quote()).isEqualTo(raw);
        assertThat(QualificationMatchPolicy.isQualificationMatch(
                requirements.certificateRequirements().getFirst(), "EDI和ICP许可证")).isTrue();
        var bid = DocumentContent.fromText("bid.pdf", "bid_hash",
                "EDI和ICP许可证\n证书编号：B2-20200044\n有效期限：2025年7月4日至2030年3月28日");
        assertThat(QualificationVetoPolicy.checkFirstStage(bid, tender, requirements, List.of()))
                .hasValueSatisfying(result -> assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS));
    }

    @Test
    void headinglessEntitiesAreDecodedOnceAndKeepOriginalEvidence() {
        String raw = "投标人须具有ISO9001&amp;#49;认证";
        var requirements = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(),
                DocumentContent.fromText("tender.pdf", "hash", raw));

        assertThat(requirements.certificateRequirements()).containsExactly("投标人须具有ISO9001&#49;认证");
        assertThat(requirements.qualificationConditions().getFirst().source().quote()).isEqualTo(raw);
        assertThat(QualificationMatchPolicy.isQualificationMatch(
                requirements.certificateRequirements().getFirst(), "ISO90011认证")).isFalse();
        assertThat(QualificationMatchPolicy.isQualificationMatch(
                requirements.certificateRequirements().getFirst(), "ISO9001&#49;认证")).isTrue();
    }

    @Test
    void encodedMarkupSurvivesParagraphClassification() {
        var conditions = QualificationConditionExtractor.extractStructured(DocumentContent.fromText(
                "tender.pdf", "hash", "七、投标人资格要求\n投标人须具有ISO&lt;b&gt;9001认证"));
        assertThat(conditions).hasSize(1);
        assertThat(conditions.getFirst().text()).isEqualTo("投标人须具有ISO<b>9001认证");
        assertThat(conditions.getFirst().certificates()).extracting(QualificationCondition.CertificateRequirement::requirementText)
                .containsExactly("投标人须具有ISO<b>9001认证");
    }

    @Test
    void encodedMarkupSurvivesTableAndRequirementsReconstruction() {
        var document = DocumentContent.fromText("tender.pdf", "hash",
                "七、投标人资格要求\n| 序号 | 资格要求 |\n| 1 | 投标人须具有ISO&lt;b&gt;9001认证及A&amp;lt;B认证 |");
        var conditions = QualificationConditionExtractor.extractStructured(document);
        assertThat(conditions).hasSize(1);
        assertThat(conditions.getFirst().text()).isEqualTo("投标人须具有ISO<b>9001认证及A&lt;B认证");
        var requirements = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(), document);
        for (int i = 0; i < 3; i++) {
            requirements = requirements.withMergedQualifications(List.of("投标人须具有营业执照"));
            assertThat(requirements.certificateRequirements()).contains("投标人须具有ISO<b>9001认证及A&lt;B认证");
            assertThat(requirements.mandatoryQualifications()).doesNotContain("投标人须具有ISO9001认证及A<B认证");
        }
    }

    @Test
    void legacyRequirementsDecodeOnceBeforeReconstruction() {
        var requirements = new TenderRequirements(List.of(), List.of("投标人须具有ISO&lt;b&gt;9001认证及A&amp;lt;B认证"),
                List.of(), List.of(), List.of(), false);
        requirements = requirements.withMergedQualifications(List.of("投标人须具有营业执照"));
        assertThat(requirements.certificateRequirements()).contains("投标人须具有ISO<b>9001认证及A&lt;B认证");
    }

    @Test
    void legacyRequirementsKeepRawQuoteAcrossReconstruction() {
        String raw = "投标人须具有ISO&#57;001认证";
        var requirements = new TenderRequirements(List.of(), List.of(raw), List.of(), List.of(), List.of(), false);
        requirements = requirements.withMergedQualifications(List.of("投标人须具有营业执照"));
        var condition = requirements.qualificationConditions().stream()
                .filter(c -> c.text().equals("投标人须具有ISO9001认证")).findFirst().orElseThrow();
        assertThat(condition.source().quote()).isEqualTo(raw);
    }

    @Test
    void vetoHtmlMatchesRetainActualSubstantivePageAndRawQuotes() {
        for (String raw : List.of("EDI和<br>ICP许可证", "ISO&#57;001认证")) {
            String nameOnly = QualificationConditionExtractor.stripHtml(raw);
            String bidText = raw + "\n证书编号：合字B2-20210088\n有效期限：2025年3月15日至2030年3月14日";
            String tenderText = "投标人须具有" + raw;
            var bid = new DocumentContent("bid.pdf", "bid", nameOnly + "\n" + bidText,
                    List.of(new DocumentContent.PageContent(2, nameOnly, List.of()),
                            new DocumentContent.PageContent(8, bidText, List.of())));
            var tender = new DocumentContent("tender.pdf", "tender", tenderText,
                    List.of(new DocumentContent.PageContent(6, tenderText, List.of())));
            var requirements = new TenderRequirements(List.of(), List.of(tenderText), List.of(), List.of(), List.of(), false);
            var result = QualificationVetoPolicy.checkFirstStage(bid, tender, requirements, List.of()).orElseThrow();
            assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
            assertThat(result.evidences()).anySatisfy(e -> {
                assertThat(e.sourceType()).isEqualTo("BID");
                assertThat(e.pageNumber()).isEqualTo(8);
                assertThat(e.quote()).contains(raw, "证书编号", "有效期限");
                assertThat(bidText).contains(e.quote());
            }).anySatisfy(e -> {
                assertThat(e.sourceType()).isEqualTo("TENDER");
                assertThat(e.pageNumber()).isEqualTo(6);
                assertThat(e.quote()).contains(raw);
                assertThat(tenderText).contains(e.quote());
            });
        }
    }

    @Test
    void tableEvidenceLocatesCleanedBrAndNumericEntities() {
        for (String raw : List.of("投标人须具有EDI和<br>ICP许可证", "投标人须具有ISO&#57;001认证")) {
            var conditions = QualificationConditionExtractor.extractStructured(DocumentContent.fromText(
                    "tender.pdf", "hash", "七、投标人资格要求\n| 序号 | 资格要求 |\n| 1 | " + raw + " |"));
            var condition = conditions.getFirst();
            assertThat(condition.certificates()).hasSize(1);
            assertThat(condition.certificates().getFirst().cellIndex()).isEqualTo(2);
            assertThat(condition.source().quote()).contains(raw);
        }
    }

    @Test
    void htmlCleaningPreservesQualificationComparisons() {
        String condition = "注册资本<100万元不得投标，营业年限>3年";
        assertThat(QualificationConditionExtractor.stripHtml(condition)).isEqualTo(condition);
        assertThat(QualificationConditionExtractor.extract("七、投标人资格要求\n1. " + condition))
                .containsExactly(condition);
    }

    @Test
    void htmlEntitiesPreserveCertificateIdentity() {
        String condition = "投标人须具有ISO&#57;001认证";
        assertThat(QualificationConditionExtractor.stripHtml(condition)).isEqualTo("投标人须具有ISO9001认证");
        var requirements = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(),
                DocumentContent.fromText("tender.pdf", "hash", "七、投标人资格要求\n1. " + condition));
        assertThat(requirements.certificateRequirements()).containsExactly("投标人须具有ISO9001认证");
        String decodedReq = requirements.certificateRequirements().getFirst();
        assertThat(QualificationMatchPolicy.isQualificationMatch(decodedReq, "ISO9001认证")).isTrue();
        assertThat(QualificationMatchPolicy.isQualificationMatch(decodedReq, "ISO14001认证")).isFalse();
        assertThat(QualificationMatchPolicy.isRawQualificationMatch(condition, "ISO9001认证")).isTrue();
        assertThat(QualificationMatchPolicy.isRawQualificationMatch(condition, "ISO14001认证")).isFalse();
    }

    @Test
    void structuredRequirementsDoNotSufferDoubleEntityDecodingInMatching() {
        // 验证结构化条件在知识库比对路径上不发生二次解码（如 &amp;#49; -> &#49; 不再被错误吞成 1）
        String rawTender = "七、投标人资格要求\n1. 投标人须具有ISO9001&amp;#49;认证";
        var requirements = DeterministicRequirementsFuser.fuseAll(TenderRequirements.empty(),
                DocumentContent.fromText("tender.pdf", "hash", rawTender));
        String decodedReq = requirements.certificateRequirements().getFirst();
        assertThat(decodedReq).isEqualTo("投标人须具有ISO9001&#49;认证");
        // 结构化匹配入口不进行二次解码，故与不同标准的 ISO90011 证书不匹配
        assertThat(QualificationMatchPolicy.isQualificationMatch(decodedReq, "ISO90011认证")).isFalse();
        assertThat(QualificationMatchPolicy.isQualificationMatch(decodedReq, "ISO9001&#49;认证")).isTrue();
    }

    @Test
    void htmlCleaningDecodesEntitiesWithoutConsumingComparisons() {
        assertThat(QualificationConditionExtractor.stripHtml(
                "<span title=\"a>b\">注册资本&lt;100万元</span>，营业年限&gt;3年，ISO&#x39;001&amp;认证&nbsp;&quot;甲&quot;&apos;乙&apos;"))
                .isEqualTo("注册资本<100万元，营业年限>3年，ISO9001&认证\u00a0\"甲\"'乙'");
        assertThat(QualificationConditionExtractor.stripHtml("EDI和<BR />ICP；未知&unknown;"))
                .isEqualTo("EDI和ICP；未知&unknown;");
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
    void longFieldFragmentsPreserveParsingAndRejectMalformedValues() {
        String longValue = "核验内容".repeat(4000);
        for (String field : List.of("conclusion", "suggestion")) {
            String prefix = "判定：\"status\":\"PASS\","
                    + (field.equals("suggestion") ? "\"conclusion\":\"完整结论\"," : "")
                    + "\"" + field + "\":\"";
            assertThat(LlmVerdictParser.parse(prefix + longValue + "\""))
                    .isPresent();
            assertThat(LlmVerdictParser.parse(prefix + longValue))
                    .isEmpty();
            assertThat(LlmVerdictParser.parse(prefix + longValue + "\", \"status\":null"))
                    .isEmpty();
        }
    }

    @Test
    void invalidTopLevelVerdictCannotBorrowNestedStatus() {
        for (String response : List.of(
                "{\"status\":null,\"conclusion\":\"无法判定\",\"example\":{\"status\":\"PASS\"}}",
                "{\"status\":\"UNKNOWN\",\"example\":{\"status\":\"PASS\"}}",
                "{\"conclusion\":\"无法判定\",\"example\":{\"status\":\"PASS\"}",
                "{\"status\":null,\"conclusion\":\"无法判定\"} {\"status\":\"PASS\"}")) {
            assertThat(LlmVerdictParser.parse(response))
                    .as("无效顶层裁决不得借用示例或后续对象：%s", response).isEmpty();
        }
    }

    @Test
    void malformedVerdictCannotCombineFieldsAcrossObjects() {
        for (String response : List.of(
                "{\"conclusion\":\"无法判定\",} {\"status\":\"PASS\"}",
                "\"conclusion\":\"无法判定\", 另一对象：\"status\":\"PASS\"",
                "\"status\":null, \"example\":{\"status\":\"PASS\"}")) {
            assertThat(LlmVerdictParser.parse(response))
                    .as("字段必须来自同一个裁决对象：%s", response).isEmpty();
        }
    }

    @Test
    void fieldFragmentRequiresCompleteNonBlankConclusion() {
        for (String status : List.of("PASS", "FAIL", "MANUAL")) {
            String prefix = "\"status\":\"" + status + "\"";
            for (String suffix : List.of("", ",", ",\"conclusion\":\"\"",
                    ",\"conclusion\":\"   \"", ",\"conclusion\":\"未完成",
                    ",\"suggestion\":\"请核查材料\"", ",\"conclusion\":null",
                    ",\"conclusion\":123")) {
                assertThat(LlmVerdictParser.parse(prefix + suffix))
                        .as("字段恢复不得补造结论：%s", prefix + suffix).isEmpty();
            }
            for (String suffix : List.of("", ",")) {
                assertThat(LlmVerdictParser.parse(
                        prefix + ",\"conclusion\":\"完整结论\"" + suffix))
                        .hasValueSatisfying(verdict -> {
                            assertThat(verdict.status()).isEqualTo(status);
                            assertThat(verdict.conclusion()).isEqualTo("完整结论");
                        });
            }
        }
    }

    @Test
    void truncatedArrayObjectsCannotReplaceCompletedVerdictFields() {
        String prefix = "{\"status\":\"FAIL\",\"conclusion\":\"缺少原始凭证\","
                + "\"suggestion\":\"请补充凭证\",\"reasonCode\":\"EVIDENCE_INSUFFICIENT\",\"details\":[";
        for (String tail : List.of(
                "{\"status\":\"PASS\",\"conclusion\":\"未完成",
                "{\"page\":1}, {\"status\":\"PASS\",\"conclusion\":\"不得借用\"",
                "{\"status\":\"PASS\",\"conclusion\":\"不得借用\", ")) {
            assertThat(LlmVerdictParser.parse(prefix + tail)).as(tail)
                    .contains(new LlmVerdict("FAIL", "EVIDENCE_INSUFFICIENT", "缺少原始凭证", "请补充凭证"));
        }
    }

    @Test
    void truncatedArrayStringsAndNestedContainersPreserveOnlyCompletedVerdict() {
        String prefix = "{\"status\":\"MANUAL\",\"conclusion\":\"需核对原图\","
                + "\"reasonCode\":\"LOW_CONFIDENCE\",";
        for (String tail : List.of(
                "\"details\":[\"未完成",
                "\"details\":[\"完整线索\", \"未完成",
                "\"details\":[\"包含转义\\\"引号及括号]}但未完成",
                "\"details\":{\"pages\":[[1,2",
                "\"details\":{\"pages\":[{\"page\":1}, ")) {
            assertThat(LlmVerdictParser.parse(prefix + tail)).as(tail)
                    .contains(new LlmVerdict("MANUAL", "LOW_CONFIDENCE", "需核对原图", ""));
        }
    }

    @Test
    void truncatedArraysCannotSupplyMissingTopLevelConclusionOrStatus() {
        for (String response : List.of(
                "{\"status\":\"PASS\",\"details\":[{\"conclusion\":\"不得借用\"",
                "{\"conclusion\":\"顶层结论\",\"details\":[{\"status\":\"PASS\"",
                "{\"status\":\"PASS\",\"conclusion\":\"\",\"details\":[\"未完成",
                "{\"status\":\"PASS\",\"conclusion\":null,\"details\":{\"pages\":[1")) {
            assertThat(LlmVerdictParser.parse(response)).as(response).isEmpty();
        }
    }

    @Test
    void truncatedVerdictRequiresCompleteNonBlankConclusion() {
        for (String status : List.of("PASS", "FAIL", "MANUAL")) {
            String prefix = "{\"status\":\"" + status + "\"";
            for (String suffix : List.of("", ",\"conclusion\":\"", ",\"conclusion\":\"尚未输出完整结论",
                    ",\"conclusion\":\"结论包含转义引号\\\"但仍未结束", ",\"conclusion\":\"\"",
                    ",\"conclusion\":\"   \"", ",\"conclusion\":null")) {
                String response = prefix + suffix;
                assertThat(LlmVerdictParser.parse(response))
                        .as("截断响应不得补造裁决：%s", response).isEmpty();
            }
            var complete = LlmVerdictParser.parse(
                    prefix + ",\"conclusion\":\"已完整输出的结论\"");
            assertThat(complete).hasValueSatisfying(verdict -> {
                assertThat(verdict.status()).isEqualTo(status);
                assertThat(verdict.conclusion()).isEqualTo("已完整输出的结论");
            });
        }
    }

    @Test
    @DisplayName("大模型响应末尾截断或丢失闭合花括号时，解析器自动修复并成功提取裁决结果")
    void truncatedOrMalformedLlmResponseIsHealedSuccessfully() {
        // 场景 1：结论已完整输出，仅建议截断；丢弃未完成建议，保留完整裁决。
        String truncated = """
                {
                  "status": "PASS",
                  "conclusion": "投标文件对各项评分标准响应完整，无占位符留白，符合满分标准。",
                  "suggestion": "建议在后续执行中保持发货时效
                """;
        var parsed1 = LlmVerdictParser.parse(truncated);
        assertThat(parsed1).isPresent();
        assertThat(parsed1.get().status()).isEqualTo("PASS");
        assertThat(parsed1.get().conclusion()).contains("符合满分标准");
        assertThat(parsed1.get().suggestion()).isEmpty();

        // 场景 2：仅丢失最外层花括号
        String missingBrace = """
                {"status": "MANUAL", "conclusion": "需人工核对系统报价", "reasonCode": "LOW_CONFIDENCE"
                """;
        var parsed2 = LlmVerdictParser.parse(missingBrace);
        assertThat(parsed2).isPresent();
        assertThat(parsed2.get().status()).isEqualTo("MANUAL");
        assertThat(parsed2.get().reasonCode()).isEqualTo("LOW_CONFIDENCE");

        // 场景 3：无外层对象的连续字段补齐后仍经 JSON 结构校验
        String brokenJson = "AI判定结果：\"status\": \"PASS\", \"conclusion\": \"响应充分\", 详情见后文...";
        var parsed3 = LlmVerdictParser.parse(brokenJson);
        assertThat(parsed3).isPresent();
        assertThat(parsed3.get().status()).isEqualTo("PASS");
        assertThat(parsed3.get().conclusion()).isEqualTo("响应充分");
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
    @DisplayName("截断评分要求在解析与合并链路均保持缺失哨兵")
    void tenderRequirementsParserRejectsTruncatedScoringBeforeMerge() {
        String truncatedJson = """
                {
                  "mandatoryQualifications": [ "增值电信业务经营许可证" ],
                  "scoringItems": [
                    {
                      "name": "投标人业绩",
                      "maxScore": 15,
                      "criteria": "按照资格要求审核"
                    },
                    {
                      "name": "投标体系认证",
                      "maxScore": 10,
                      "criteria": "ISO认证
                """;
        var parsed = TenderRequirementsParser.parse(truncatedJson);
        assertThat(parsed).isEqualTo(TenderRequirements.missingSentinel());
        assertThat(TenderRequirements.merge(List.of(parsed)))
                .isEqualTo(TenderRequirements.missingSentinel());
        assertThat(parsed.missing()).isTrue();
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
    void crossSentenceThirdPartyOwnershipBlocksEntryAndFinalBoundary() {
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION,
                "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        for (String separator : List.of("。", "\n", "。\n")) {
            for (String ownership : List.of("该证书属于供应商", "该认证归属第三方",
                    "该证书持证人为关联公司", "该证书为合作方所有",
                    "该证书由认证机构颁发。该证书属于供应商",
                    "该证书属于制造商", "持证人为生产厂家", "该证书为代理商所有", "实际由制造商持有",
                    "该证书不属于本公司", "该认证非我司所有", "该资质不归属于本投标人")) {
                var bid = DocumentContent.fromText("bid", "hash",
                        "本公司持有ISO9001认证证书" + separator + ownership + "。");
                org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
                    softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid,
                            DocumentContent.empty(), req, List.of(evidence)).orElseThrow().status())
                            .as("入口: %s", bid.fullText()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
                    var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO,
                            "通过", List.of(evidence));
                    softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                            .as("最终边界: %s", bid.fullText()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
                    var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(),
                            List.of(match), req, java.util.Set.of()).stream()
                            .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                            .findFirst().orElseThrow();
                    softly.assertThat(result.status()).as("引擎: %s", bid.fullText())
                            .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
                });
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "该证书持证单位为本公司的供应商, MANUAL_REVIEW",
            "该证书属于本公司母公司, MANUAL_REVIEW",
            "该证书属于我司的子公司, MANUAL_REVIEW",
            "该认证持有单位：本投标人的合作方, MANUAL_REVIEW",
            "该证书持证主体为投标人的供应商, MANUAL_REVIEW",
            "该证书属于 本公司, PASS",
            "该证书属于： 本公司, PASS",
            "该证书持证单位为 我司, PASS",
            "该认证持有单位： 本投标人, PASS",
            "该证书持证主体为： 投标人, PASS",
            "该证书属于　我方, PASS"})
    void completeOwnershipSubjectControlsEntryBoundaryAndEngine(String ownership, QualityCheckStatus expected) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        var bid = DocumentContent.fromText("bid", "hash", "本公司具有ISO9001认证。" + ownership + "。");
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口: %s", ownership).isEqualTo(expected);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界: %s", ownership).isEqualTo(expected);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎: %s", ownership).isEqualTo(expected);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "未曾获得", "未能取得", "未曾持有", "未能通过", "未具有", "未具备"})
    void deniedCertificationCannotBeOverriddenByKnowledgeEvidence(String denial) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String text = "本公司具有ISO9001认证。经核实本公司" + denial + "该认证。";
        var bid = DocumentContent.fromText("bid", "hash", text);
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证"))
                    .as("持证事实: %s", denial).isFalse();
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口: %s", denial)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界: %s", denial).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎: %s", denial).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "该证书不是本公司的", "该认证并非我司的", "该资质并不是我方的",
            "该证书不是本投标人的", "ISO9001认证不是投标人的", "该证书不是　 本公司　 的",
            "该证书不是本公司持有", "该证书并非我司持有", "该认证并不是我方取得",
            "该证书不是由本投标人持有", "该证书不是　 本公司　 持有",
            "ISO9001认证不是投标人持有",
            "该证书不再属于本公司", "该证书已不属于我司", "该认证不再归属于我方",
            "该证书并非归属本投标人", "该证书不是属于投标人", "该证书不再 属于　 本公司"})
    void deniedBidderOwnershipCannotBeOverriddenByKnowledgeEvidence(String denial) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String text = "本公司具有ISO9001认证。" + denial + "。";
        var bid = DocumentContent.fromText("bid", "hash", text);
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证"))
                    .as("持证事实: %s", denial).isFalse();
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口: %s", denial)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界: %s", denial).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎: %s", denial).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        });
    }

    @Test
    void unrelatedDefectsAndUnexpiredCertificatePreserveKbPass() {
        var evidence = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true));
        for (String text : List.of("本公司具有ISO9001认证（证书编号：ABCD1234）。",
                "本公司具有ISO9001认证。该证书编号：ABCD1234。",
                "本公司具有ISO9001认证，售后承诺缺页。",
                "本公司持有ISO9001认证，证书未过期。", "本公司具有ISO9001认证。",
                "本公司具有ISO9001认证。未附售后服务承诺。",
                "本公司具有ISO9001认证。经核对报价表未附。",
                "本公司具有ISO9001认证。售后承诺未提供清晰的扫描件。",
                "本公司具有ISO9001认证。第二章 合同材料未提交加盖公章的复印件。",
                "本公司具有ISO9001认证。ISO14001认证未提供清晰的扫描件。",
                "本公司具有ISO9001认证。经核对报价表尚未提供。",
                "本公司具有ISO9001认证。经核对报价表缺失。",
                "本公司具有ISO9001认证。经现场复核确认报价表暂时无法提供。",
                "本公司具有ISO9001认证。售后服务承诺缺少。",
                "本公司具有ISO9001认证。经核对售后服务承诺未提交。",
                "本公司具有ISO9001认证。ISO14001认证。该证书属于供应商。",
                "本公司具有ISO9001认证。该证书归属本公司。",
                "本公司具有ISO9001认证。该证书持证单位为本公司。",
                "本公司具有ISO9001认证。该认证持有单位：我司。",
                "本公司具有ISO9001认证。该证书持证主体为本投标人。",
                "本公司具有ISO9001认证。该证书由认证机构颁发。",
                "本公司具有ISO9001认证。待补报价表。",
                "本公司具有ISO9001认证。合同。权属待议。",
                "本公司具有ISO9001认证。合同。仅为借用。",
                "本公司具有ISO9001认证。合同。实际借用自合作方。",
                "本公司具有ISO9001认证。ISO14001认证。实际借用自合作方。",
                "本公司具有ISO9001认证。\n填写示例：\n实际借用自合作方。",
                "本公司具有ISO9001认证。ISO14001认证。实际为借用。",
                "本公司具有ISO9001认证。\n填写示例：\n仅为借用。",
                "本公司具有ISO9001认证。报价。状态待确认。",
                "本公司具有ISO9001认证。\n填写示例：\n权属待议。状态待确认。",
                "本公司具有ISO9001认证。ISO14001认证。该证书已过期。")) {
            var bid = DocumentContent.fromText("bid", "hash", text);
            var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                    List.of(), List.of(), List.of(), false);
            assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as(text).isEqualTo(QualityCheckStatus.PASS);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).as(text)
                    .isEqualTo(QualityCheckStatus.PASS);
        }
    }

    @Test
    void unrelatedNamedCertificateDefectsDoNotDemoteKbPass() {
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        var evidence = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true));
        for (String separator : List.of("。", "，")) {
            for (String defect : List.of("施工资质证书复印件未附", "营业执照复印件未附",
                    "安全生产许可证已过期")) {
                var bid = DocumentContent.fromText("bid", "hash",
                        "本公司具有ISO9001认证" + separator + defect + "。");
                var result = QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                        List.of(evidence)).orElseThrow();
                assertThat(result.status()).as(bid.fullText()).isEqualTo(QualityCheckStatus.PASS);
                var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO,
                        "通过", List.of(evidence));
                assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).as(bid.fullText())
                        .isEqualTo(QualityCheckStatus.PASS);
            }
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
    void certificatePronounContinuationCannotBypassContradictionCheck() {
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        var evidence = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true));
        for (String text : List.of("本公司具有ISO9001认证。该证书已过期。",
                "本公司具有ISO9001认证。该证书由认证机构颁发。该证书已过期。",
                "本公司具有ISO9001认证。该证书由认证机构颁发。该认证登记于本公司。现已失效。",
                "本公司具有ISO9001认证，但已过期。",
                "本公司具有ISO9001认证，该证书已吊销。",
                "本公司具有ISO9001认证。该认证已失效。",
                "本公司具有ISO9001认证。\n该证书尚未取得。",
                "该证书已吊销。\n本公司具有ISO9001认证。",
                "本公司具有ISO9001认证，但目前该证书已过期。",
                "本公司具有ISO9001认证，但此证已失效。",
                "本公司具有ISO9001认证，但尚未取得该证书。",
                "本公司具有ISO9001认证，经核实该证书已过期。",
                "本公司具有ISO9001认证。现已失效。",
                "本公司具有ISO9001认证。暂时未取得。")) {
            var bid = DocumentContent.fromText("bid", "hash", text);
            assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as(text)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO,
                    "通过", List.of(evidence));
            assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).as(text)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }

    @Test
    void thirdPartyCertificateHoldersRequireReviewAtEntryBoundaryAndEngine() {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        for (String holder : List.of("供应商", "合作方", "合作企业", "第三方", "关联公司")) {
            for (String ownership : List.of("该证书由" + holder + "持有", "该证书由" + holder + "取得",
                    "该证书为" + holder + "持有", "该证书持有人为" + holder)) {
                var bid = DocumentContent.fromText("bid", "hash", "本公司持有ISO9001认证证书。" + ownership + "。");
                assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                        List.of(evidence)).orElseThrow().status()).as(bid.fullText())
                        .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
                var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
                assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).as(bid.fullText())
                        .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
                var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(),
                        List.of(match), req, java.util.Set.of()).stream()
                        .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                        .findFirst().orElseThrow();
                assertThat(result.status()).as(bid.fullText()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "该证书由上海甲公司持有, MANUAL_REVIEW",
            "该证书由北京乙有限公司持有, MANUAL_REVIEW",
            "该证书由上海甲公司取得, MANUAL_REVIEW",
            "该证书由本公司的合作方持有, MANUAL_REVIEW",
            "该证书由 本公司 持有, PASS",
            "该证书由我司取得, PASS",
            "该证书由本投标人持有, PASS",
            "ISO14001认证。该证书由上海甲公司持有, PASS",
            "合同由上海甲公司持有, PASS"
    })
    void namedCertificateHolderRequiresConfirmedBidderIdentity(String ownership, QualityCheckStatus expected) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        var bid = DocumentContent.fromText("bid", "hash", "本公司具有ISO9001认证。" + ownership + "。");
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(bid.fullText(), "ISO9001认证"))
                    .as("纯核心: %s", ownership).isEqualTo(expected == QualityCheckStatus.PASS);
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口: %s", ownership).isEqualTo(expected);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界: %s", ownership).isEqualTo(expected);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(),
                    List.of(match), req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎: %s", ownership).isEqualTo(expected);
        });
    }

    @Test
    void explanatorySentencesCannotEraseCertificateContradictionsAtEntryAndBoundary() {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        for (String explanation : List.of("本公司于2020年获证", "本公司于2020年获证。相关资料由行政部门保管")) {
            for (String defect : List.of("该证书已过期", "该认证已吊销", "该证书属于供应商", "该证书有效期至2025年12月31日")) {
                var bid = DocumentContent.fromText("bid", "hash", "本公司具有ISO9001认证。" + explanation + "。" + defect + "。");
                assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                        List.of(evidence)).orElseThrow().status()).as(bid.fullText())
                        .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
                var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
                assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).as(bid.fullText())
                        .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            }
        }
    }

    @Test
    void engineKeepsExpiredCertificateForReviewAcrossExplanatorySentences() {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        for (String explanation : List.of("本公司于2020年获证", "本公司于2020年获证。相关资料由行政部门保管")) {
            var bid = DocumentContent.fromText("bid", "hash", "本公司具有ISO9001认证。" + explanation + "。该证书已过期。");
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(),
                    List.of(match), req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            assertThat(result.status()).as(bid.fullText()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }

    @Test
    void formattedQualificationSummaryPreservesCertificateIdentityThroughEngine() throws Exception {
        var formatter = Class.forName("com.xiyu.bid.compliance.service.KnowledgeMatchFormatters")
                .getDeclaredMethod("describe", com.xiyu.bid.entity.Qualification.class);
        formatter.setAccessible(true);
        String name = "增值电信业务经营许可证（EDI和ICP）";
        String summary = (String) formatter.invoke(null, com.xiyu.bid.entity.Qualification.builder()
                .name(name).level("国家级").expiryDate(LocalDate.of(2028, 1, 1)).build());
        var evidence = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                KnowledgeMatch.SOURCE_QUALIFICATION, "record", summary, true));
        var bid = DocumentContent.fromText("bid", "hash", "本公司具有" + name + "。");
        var req = new TenderRequirements(List.of(), List.of("投标人须具有" + name),
                List.of(), List.of(), List.of(), false);
        var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(),
                List.of(new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", evidence.quote(), true)),
                    req, java.util.Set.of()).stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                .findFirst().orElseThrow();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.evidences()).contains(evidence);
    }

    @Test
    void expiredValidityContinuationCannotPassEngine() {
        var evidence = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true));
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        for (String continuation : List.of("有效期已过期", "有效期限已失效", "但有效期已过期",
                "该证书由认证机构颁发。该证书已过期")) {
            var bid = DocumentContent.fromText("bid", "hash", "本公司具有ISO9001认证。" + continuation + "。");
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(),
                    List.of(new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", evidence.quote(), true)),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            assertThat(result.status()).as(continuation).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }

    @Test
    void datedCertificateValidityRequiresConfirmationDespiteValidKbRecord() {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION,
                "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        for (String validity : List.of("该证书有效期至2025年12月31日",
                "有效期限：2025-12-31", "该认证有效期至2025/12/31",
                "该证书截止日期：2025.12.31", "该证书有效期至2099年12月31日")) {
            var bid = DocumentContent.fromText("bid", "hash", "本公司具有ISO9001认证。" + validity + "。");
            assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as(validity)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var proposedPass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO,
                    "通过", List.of(evidence));
            assertThat(SemanticEvidenceBoundary.reconcile(proposedPass, bid).status()).as(validity)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(),
                    List.of(match), req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            assertThat(result.status()).as(validity).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
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
    void authorizationTitleAliasesRequireSignatureEvidenceThroughEngine() {
        for (String title : AuthorizationLetterCheckPolicy.AUTH_LETTER_KEYWORDS) {
            for (boolean filled : List.of(false, true)) {
                String text = "【页面图像转写，非验签】" + title
                        + "\n法定代表人：张三 被授权人：李四 授权事项：代表本公司参加投标 授权期限：2026年1月1日至2026年12月31日"
                        + (filled ? "\n法定代表人（签字）：张三 委托代理人（签字）：李四 投标人：（盖章）"
                                  : "\n法定代表人（签字）：____ 委托代理人（签字）：____ 投标人：（盖章）");
                var bid = new DocumentContent("bid", "hash", text,
                        List.of(new DocumentContent.PageContent(1, text, List.of())),
                        List.of(new com.xiyu.bid.compliance.domain.PageImage(
                                0, "image/png", title, 1, 0, 0, "base64")));
                var expected = filled ? QualityCheckStatus.PASS : QualityCheckStatus.MANUAL_REVIEW;
                var pass = QualityCheckItemResult.pass(BidQualityCheckItem.AUTHORIZATION_LETTER,
                        "字段齐全", List.of());
                assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).as(title + filled)
                        .isEqualTo(expected);
                var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(),
                        List.of(), TenderRequirements.empty(), java.util.Set.of()).stream()
                        .filter(r -> r.itemCode().equals(BidQualityCheckItem.AUTHORIZATION_LETTER.getCode()))
                        .findFirst().orElseThrow();
                assertThat(result.status()).as(title + filled).isEqualTo(expected);
            }
        }
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "签名：无", "签字：未提供", "盖章：无", "签署：未提供",
            "签名：未填写", "盖章：未加盖", "签字：尚未填写", "公章：尚未加盖",
            "签名：没有填写", "印章：没有加盖", "签名：待填写", "盖章：待加盖",
            "图像可见手写签名。签名：未填写", "图像可见公章。盖章：未加盖"})
    void explicitMissingSignatureFieldCannotPassThroughEngine(String signature) {
        String text = "【页面图像转写，非验签】授权委托书\n法定代表人：张三\n受托人：李四"
                + "\n授权范围：提交投标文件\n授权期限：长期有效\n" + signature;
        var bid = new DocumentContent("bid", "hash", text,
                List.of(new DocumentContent.PageContent(1, text, List.of())),
                List.of(new com.xiyu.bid.compliance.domain.PageImage(
                        0, "image/png", "授权委托书", 1, 0, 0, "base64")));
        assertThat(AuthorizationFieldPolicy.assessAuthorizationFields(text).filled()).isTrue();
        var pass = QualityCheckItemResult.pass(BidQualityCheckItem.AUTHORIZATION_LETTER, "要素齐全", List.of());
        assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).as(signature)
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(),
                List.of(), TenderRequirements.empty(), java.util.Set.of()).stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.AUTHORIZATION_LETTER.getCode()))
                .findFirst().orElseThrow();
        assertThat(result.status()).as("引擎: %s", signature).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(AuthorizationFieldPolicy.hasSignedAuthorizationEvidence(bid)).isFalse();
    }

    @Test
    void blankOrNegatedSignatureLabelsWithImageCannotKeepPass() {
        for (String signature : List.of(
                "法定代表人（签字）：____ 投标人：（盖章）",
                "法定代表人（签字）：[此处签字] 投标人：（盖章）",
                "未签字、未盖章", "请签字并盖章", "须提供手写签名及公章印迹",
                "图像可见签字栏及盖章栏，待签署盖章", "图像可见手写签名，未盖章",
                "未签署未盖章", "签字：空缺；公章：空缺。图像可见公章栏。",
                "图像可见公章栏。", "图像可见公章栏", "图像可见签字栏",
                "签字：空缺", "公章：空缺", "签字栏：空缺", "公章处：缺少",
                "公章不可见", "未见公章", "签署人（签字）：待签", "签署人（签字）：待定",
                "法定代表人（签字）：待签", "委托代理人（签字）：待签", "受托人（签字）：待签署", "签署人（签字）：未签署")) {
            String text = "【页面图像转写，非验签】法定代表人授权委托书\n授权人：张三 被授权人：李四 "
                    + "授权事项：参与投标 授权期限：2026年1月1日至2026年12月31日\n" + signature;
            var bid = new DocumentContent("bid", "hash", text,
                    List.of(new DocumentContent.PageContent(1, text, List.of())),
                    List.of(new com.xiyu.bid.compliance.domain.PageImage(
                            0, "image/png", "授权委托书", 1, 0, 0, "base64")));
            assertThat(AuthorizationFieldPolicy.hasSignedAuthorizationEvidence(bid)).as(signature).isFalse();
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.AUTHORIZATION_LETTER, "要素齐全", List.of());
            assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).as(signature)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(),
                    List.of(), TenderRequirements.empty(), java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.AUTHORIZATION_LETTER.getCode()))
                    .findFirst().orElseThrow();
            assertThat(result.status()).as("引擎: %s", signature).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "签署人（签字）：\n投标人：（盖章）", "签署人（签字）：\r\n投标人：（盖章）",
            "签署人（签字）： 投标人：（盖章）", "签署人（签字）：\n公司：西域公司",
            "签署人（签字）：，投标人：（盖章）", "签字： | 投标人：（盖章）",
            "签署人（签字）：张三丰李四", "签署人（签字）：投标人 （盖章）"})
    void blankSignatureCannotConsumeFollowingField(String signature) {
        String text = "【页面图像转写，非验签】法定代表人授权委托书\n法定代表人：张三 被授权人：李四 "
                + "授权事项：参与投标 授权期限：2026年1月1日至2026年12月31日\n" + signature;
        var bid = new DocumentContent("bid", "hash", text,
                List.of(new DocumentContent.PageContent(1, text, List.of())),
                List.of(new com.xiyu.bid.compliance.domain.PageImage(
                        0, "image/png", "授权委托书", 1, 0, 0, "base64")));
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(AuthorizationFieldPolicy.assessAuthorizationFields(text).filled()).as("完整字段").isTrue();
            softly.assertThat(AuthorizationFieldPolicy.hasValidAuthorizationContent(text)).as("正文").isFalse();
            softly.assertThat(AuthorizationFieldPolicy.hasSignedAuthorizationEvidence(bid)).as("签署证据").isFalse();
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.AUTHORIZATION_LETTER, "要素齐全", List.of());
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界").isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(),
                    TenderRequirements.empty(), java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.AUTHORIZATION_LETTER.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎").isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "公章是否可见尚待核实", "公章并不清晰可见", "公章不清晰可见",
            "公章可能可见", "公章清晰可见尚待核实", "公章可见？",
            "是否可见公章", "不清晰可见公章", "图像可能可见公章",
            "图像可见公章但尚待核实", "图像可见疑似公章", "图像可见公章吗",
            "不确定是否已签字", "是否已签字？", "已签字尚待核实",
            "不确定是否已签名", "是否已签名？", "已签名尚待核实",
            "不确定是否已盖章", "是否已盖章？", "已盖章尚待核实",
            "不确定是否已加盖公章", "是否已加盖公章？", "已加盖公章尚待核实",
            "不确定 法定代表人（签字）：张三", "可能\t法定代表人（签字）：张三",
            "尚待核实　法定代表人（签字）：张三", "无法确认，法定代表人（签字）：张三",
            "签署人（签字）：张三 不确定", "法定代表人（签字）：张三，可能签署",
            "疑似法定代表人（签字）：张三", "是否法定代表人（签字）：张三",
            "法定代表人（签字）：张三，真实性待核实", "法定代表人（签字）：张三，签字存疑"})
    void uncertainSignatureObservationCannotPassThroughEngine(String signature) {
        String text = "【页面图像转写，非验签】授权委托书\n法定代表人：张三\n受托人：李四"
                + "\n授权范围：提交投标文件\n授权期限：长期有效\n" + signature;
        var bid = new DocumentContent("bid", "hash", text,
                List.of(new DocumentContent.PageContent(1, text, List.of())),
                List.of(new com.xiyu.bid.compliance.domain.PageImage(
                        0, "image/png", "授权委托书", 1, 0, 0, "base64")));
        assertThat(AuthorizationFieldPolicy.assessAuthorizationFields(text).filled()).isTrue();
        var pass = QualityCheckItemResult.pass(BidQualityCheckItem.AUTHORIZATION_LETTER, "要素齐全", List.of());
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(AuthorizationFieldPolicy.hasValidAuthorizationContent(text)).as("完整签署陈述").isFalse();
            softly.assertThat(AuthorizationFieldPolicy.hasSignedAuthorizationEvidence(bid)).as("同页签署实证").isFalse();
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).as("最终边界")
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(),
                    TenderRequirements.empty(), java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.AUTHORIZATION_LETTER.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎").isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "签署人（签字）：\n图像可见公章。",
            "签署人（签字）\n图像可见公章。",
            "法定代表人(签字) \r\n图像可见公章。",
            "签字\n图像可见公章。",
            "图像可见公章。\n签署人（签字）",
            "| 签署人（签字） | 投标人：（盖章） |\n图像可见公章。",
            "签署人（签字）： 投标人：（盖章）\n图像可见公章。",
            "签署人（签字）：受托人：李四\n图像可见公章。",
            "法定代表人（签字）： 公司：西域公司\n图像可见公章。",
            "签署人（签字）： \r\n图像可见公章。",
            "图像可见公章。\n签署人（签字）：",
            "法定代表人（签字）：\n图像可见公章。",
            "签字：\n图像可见公章。",
            "公章：\n图像可见手写签名。",
            "签署人（签字）：；图像可见公章。",
            "签署人（签字）：，投标人：（盖章）\n图像可见公章。",
            "签字： |\n图像可见公章。",
            "| 签字： | 投标人：（盖章） |\n图像可见公章。",
            "图像可见公章。\n签字：暂未完成。",
            "图像可见公章。\n签字：不完整。",
            "图像可见公章。签字：部分完成。",
            "图像可见公章。法定代表人（签字）：部分完成。",
            "图像可见公章。签字：仅部分完成。",
            "图像可见公章。签字：尚待确认。",
            "图像可见公章。\n签字：未完成。",
            "图像可见公章。\n签字：不齐全。",
            "图像可见公章。\n法定代表人（签字）：暂未完成。",
            "图像可见公章。\n签名：没有。",
            "图像可见公章。\n签字: 没有。",
            "图像可见手写签名。\n公章：没有。",
            "图像可见公章。\n法定代表人（签字）：没有。",
            "图像可见手写签名。\n公章不可见。",
            "公章清晰可见。\n手写签名不可见。",
            "图像可见公章。\n签名未见。",
            "图像可见公章。\n印章模糊不清。",
            "公章清晰可见。\n无法辨认签字。",
            "签署人（签字）：张三\n公章不可见。",
            "法定代表人（签字）：张三\n公章缺失。"})
    void mixedPositiveAndNegativeSignatureDescriptionsMustBlockPass(String mixedObservations) {
        String text = "【页面图像转写，非验签】授权委托书\n法定代表人：张三\n受托人：李四"
                + "\n授权范围：提交投标文件\n授权期限：长期有效\n" + mixedObservations;
        var bid = new DocumentContent("bid", "hash", text,
                List.of(new DocumentContent.PageContent(1, text, List.of())),
                List.of(new com.xiyu.bid.compliance.domain.PageImage(
                        0, "image/png", "授权委托书", 1, 0, 0, "base64")));
        var pass = QualityCheckItemResult.pass(BidQualityCheckItem.AUTHORIZATION_LETTER, "要素齐全", List.of());
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(AuthorizationFieldPolicy.hasValidAuthorizationContent(text))
                    .as("完整授权正文: %s", mixedObservations).isFalse();
            softly.assertThat(AuthorizationFieldPolicy.hasValidAuthorizationContent(mixedObservations))
                    .as("内容有效性: %s", mixedObservations).isFalse();
            softly.assertThat(AuthorizationFieldPolicy.hasSignedAuthorizationEvidence(bid))
                    .as("签署实证: %s", mixedObservations).isFalse();
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界: %s", mixedObservations).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(),
                    TenderRequirements.empty(), java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.AUTHORIZATION_LETTER.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎: %s", mixedObservations)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        });
    }

    @Test
    void validPositiveVisualSignatureOrStampKeepsPass() {
        for (String signature : List.of(
                "图像可见公章", "图像可见手写签名", "公章清晰可见", "手写签名清晰可见",
                "加盖有鲜红公章", "加盖有印章", "签署人（签字）：张三",
                "签署人（签字）张三", "法定代表人(签字) 李四",
                "已签字", "已签名。", "已盖章；", "已加盖公章",
                "签署人（签字）： 李四\n投标人：（盖章）", "委托代理人签名：王小明；日期：2026年1月1日")) {
            String text = "【页面图像转写，非验签】法定代表人授权委托书\n法定代表人：张三 被授权人：李四 "
                    + "授权事项：参与投标 授权期限：2026年1月1日至2026年12月31日\n" + signature;
            var bid = new DocumentContent("bid", "hash", text,
                    List.of(new DocumentContent.PageContent(1, text, List.of())),
                    List.of(new com.xiyu.bid.compliance.domain.PageImage(
                            0, "image/png", "授权委托书", 1, 0, 0, "base64")));
            assertThat(AuthorizationFieldPolicy.hasSignedAuthorizationEvidence(bid)).as(signature).isTrue();
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.AUTHORIZATION_LETTER, "要素齐全", List.of());
            assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).as(signature)
                    .isEqualTo(QualityCheckStatus.PASS);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
                "该证书的电子档案存储于服务器NAS01。该证书已过期",
                "电子档案存储于服务器NAS01。该证书已过期",
                "电子档案存储于服务器CS01。该证书已过期",
                "电子档案存储于服务器ITSS01。该证书已过期",
                "电子档案存储于服务器CCRC01。该证书已过期",
                "该证书的档案编号为DOC2026。经查已吊销",
                "该证书存储于NAS01且已过期",
                "证书编号：CN12345。该证书已过期",
                "证书编号为CN12345。该证书已过期",
                "证书编号是CN12345。该证书已过期",
                "认证编号为ISO-12345。该证书已过期",
                "编号为CN12345。该证书已过期",
                "认证编号：CN12345。经查已吊销",
                "证号：ISO14001-12345。该证书已过期",
                "该证书持证单位为供应商",
                "该证书持证单位：合作企业",
                "持证单位为制造商",
                "该认证持有单位为第三方",
                "该证书持证主体为关联公司",
                "持证人为供应商",
                "实际由第三方持有",
                "持有人为关联公司",
                "证书编号：CN12345。持证人为供应商",
                "经核对复印件尚未提供",
                "经核对扫描件尚未提交",
                "经核对原件尚未上传",
                "经核对证明材料尚未附具",
                "经核对尚未提供复印件",
                "经核对复印件未附",
                "经现场复核确认扫描件未提交",
                "经重新审核证明材料暂缺",
                "核对结果为未附复印件",
                "复印件未附",
                "复印件待补",
                "扫描件未附",
                "原件后补",
                "证明材料暂缺",
                "复印件缺页",
                "未附复印件",
                "但是复印件未附",
                "资料由行政部门保管。复印件待补",
                "该证书已到期",
                "该证书已于2025年12月31日到期",
                "该证书于2025年12月31日到期",
                "但是已于2025年12月31日到期",
                "该证书已逾期",
                "已到期",
                "但是已过期",
                "不过已吊销",
                "已注销",
                "已作废",
                "但是该证书已过期",
                "不过该证书已吊销",
                "经审核确认后该认证已失效",
                "经审核确认后已过期",
                "经查已吊销",
                "经现场复核确认现已失效",
                "资料由行政部门保管。经重新审核确认后已作废",
                "负责认证的机构为中国质量认证中心。该证书已过期",
                "附件包含证书复印件。该证书已过期",
                "认证资料由行政部门保管。该证书已吊销",
                "该证书有效期已届满",
                "该证书不再有效",
                "该证书已作废",
                "该证书已注销",
                "该证书有效期限：\n2025年12月31日",
                "该证书有效期限：\r\n2025-12-31",
                "该证书截止日期：\n2025.12.31",
                "该证书到期日期：\n２０２５年１２月３１日",
                "该证书有效期限：\n2099年12月31日"})
    void ordinaryCertificationExplanationAndWrappedValidityCannotPass(String continuation) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
            List.of(), List.of(), List.of(), false);
        var bid = DocumentContent.fromText("bid", "hash", "本公司具有ISO9001认证。" + continuation + "。");
        assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(bid.fullText(), "ISO9001认证"))
                .as(continuation).isFalse();
        assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                List.of(evidence)).orElseThrow().status()).as("入口: %s", continuation)
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
        assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).as("最终边界: %s", continuation)
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                req, java.util.Set.of()).stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                .findFirst().orElseThrow();
        assertThat(result.status()).as("引擎: %s", continuation).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "安全生产许可证", "建筑企业资质证书", "环境管理体系认证", "ISO14001认证", "ABC123认证", "CS01认证"})
    void namedCertificateSwitchesContextBeforeDefect(String otherCertificate) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        for (String defect : List.of("权属待议", "状态待确认", "该证书已过期", "经审核确认后已过期", "复印件未附", "复印件待补", "经核对复印件未附", "经重新审核证明材料暂缺", "经核对复印件尚未提供", "经核对尚未提供复印件", "扫描件缺失", "经核对扫描件缺失", "复印件缺少", "经现场复核确认扫描件暂时无法提供", "经现场复核确认暂时无法提供扫描件")) {
            var bid = DocumentContent.fromText("bid", "hash",
                    "本公司具有ISO9001认证。" + otherCertificate + "。" + defect + "。");
            assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口: %s / %s", otherCertificate, defect)
                    .isEqualTo(QualityCheckStatus.PASS);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).as("最终边界: %s / %s", otherCertificate, defect)
                    .isEqualTo(QualityCheckStatus.PASS);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            assertThat(result.status()).as("引擎: %s / %s", otherCertificate, defect).isEqualTo(QualityCheckStatus.PASS);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "ISO14001认证。证书编号：CN12345", "安全生产许可证。证书编号：CN12345",
            "ISO14001认证", "安全生产许可证",
            "ISO14001认证。持证单位为供应商", "安全生产许可证。持证单位为供应商"})
    void otherCertificateOwnershipDoesNotDemoteCurrentCertificate(String otherCertificate) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        var bid = DocumentContent.fromText("bid", "hash",
                "本公司具有ISO9001认证。" + otherCertificate + "。持证人为供应商。");
        assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                List.of(evidence)).orElseThrow().status()).as("入口: %s", otherCertificate)
                .isEqualTo(QualityCheckStatus.PASS);
        var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
        assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).as("最终边界: %s", otherCertificate)
                .isEqualTo(QualityCheckStatus.PASS);
        var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                req, java.util.Set.of()).stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                .findFirst().orElseThrow();
        assertThat(result.status()).as("引擎: %s", otherCertificate).isEqualTo(QualityCheckStatus.PASS);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "填写示例（非本公司实际声明）：\n本公司具有ISO9001认证。",
            "填报示例：\n本公司具有ISO9001认证。",
            "填写示例（非本公司实际声明）：\n\n本公司具有ISO9001认证。",
            "填报示例：\n \n\n本公司具有ISO9001认证。",
            "填写示例：\n一、资质声明\n本公司具有ISO9001认证。",
            "格式样例：\n本公司具有ISO9001认证。",
            "参考格式：\n本公司具有ISO9001认证。",
            "填写说明：\n本公司具有ISO9001认证。",
            "非本公司实际声明：\n本公司具有ISO9001认证。",
            "资质声明模板\n本公司具有ISO9001认证。",
            "合同样例\n本公司具有ISO9001认证。",
            "声明模板\n本公司具有ISO9001认证。",
            "合同模板\n本公司具有ISO9001认证。",
            "合同样例（仅供参考）\n本公司具有ISO9001认证。"})
    void exampleHeaderAcrossLinesPreservesManualReview(String text) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        var bid = DocumentContent.fromText("bid", "hash", text);
        assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证")).isFalse();
        assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                List.of(evidence)).orElseThrow().status()).as("入口: %s", text)
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
        assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).as("最终边界: %s", text)
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                req, java.util.Set.of()).stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                .findFirst().orElseThrow();
        assertThat(result.status()).as("引擎: %s", text).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"二、正式声明", "正式声明"})
    void legitimateStatementAfterExampleBlockPasses(String boundary) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String text = "填写示例（非本公司实际声明）：\n本公司具有ISO9001认证。\n\n" + boundary + "\n本公司具有ISO9001质量管理体系认证证书。";
        var bid = DocumentContent.fromText("bid", "hash", text);
        assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证")).isTrue();
        assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                List.of(evidence)).orElseThrow().status())
                .isEqualTo(QualityCheckStatus.PASS);
        var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
        assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                .isEqualTo(QualityCheckStatus.PASS);
        var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                req, java.util.Set.of()).stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                .findFirst().orElseThrow();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "资质声明模板\n本公司具有ISO9001认证。\n\n正式声明：\n本公司具有ISO9001质量管理体系认证证书。",
            "合同样例\n本公司具有ISO9001认证。\n\n正式声明：\n本公司具有ISO9001质量管理体系认证证书。",
            "合同模板\n本公司具有ISO9001认证。\n\n正式正文：\n本公司具有ISO9001质量管理体系认证证书。"})
    void legitimateStatementAfterTemplateHeadingPasses(String text) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        var bid = DocumentContent.fromText("bid", "hash", text);
        assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证")).isTrue();
        assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                List.of(evidence)).orElseThrow().status())
                .isEqualTo(QualityCheckStatus.PASS);
        var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
        assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                .isEqualTo(QualityCheckStatus.PASS);
        var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                req, java.util.Set.of()).stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                .findFirst().orElseThrow();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "本公司具有ISO9001认证。该证书复印件不提供。",
            "本公司具有ISO9001认证。该证书复印件拒绝提供。",
            "本公司具有ISO9001认证。该证书扫描件拒绝提交。",
            "本公司具有ISO9001认证。经核对拒绝提供扫描件。",
            "本公司具有ISO9001认证。该证书原件拒绝出示。",
            "本公司具有ISO9001认证。该证书复印件不随标书提交。",
            "本公司具有ISO9001认证。该证书扫描件不随本次投标文件提交。",
            "本公司具有ISO9001认证。经核对不提供扫描件。",
            "本公司具有ISO9001认证。经核对复印件不提交。",
            "本公司具有ISO9001认证。该证书原件不出示。",
            "本公司具有ISO9001认证。经核实本公司不再持有该认证。",
            "本公司具有ISO9001认证。经核实本公司不再具备该资质。",
            "本公司具有ISO9001认证。该证书扫描件未随标书提交。",
            "本公司具有ISO9001认证。该证书复印件尚未随投标文件提交。",
            "本公司具有ISO9001认证。经核对未提供清晰的扫描件。",
            "本公司具有ISO9001认证。经现场审核未提交加盖公章的复印件。",
            "本公司具有ISO9001认证。经复核证明材料目前仍然缺失。",
            "本公司具有ISO9001认证。经现场确认暂时无法出示可供核验的原件。",
            "本公司具有ISO9001认证。经核对本公司未取得认证。",
            "本公司具有ISO9001认证。经核对本公司未持有证书。",
            "本公司具有ISO9001认证。经核对不具备资质。",
            "本公司具有ISO9001认证。经核对未通过认证。",
            "本公司具有ISO9001认证。经核对扫描件暂不能提供。",
            "本公司具有ISO9001认证。经现场复核确认扫描件暂时无法提供。",
            "本公司具有ISO9001认证。经现场复核确认暂时无法提供扫描件。",
            "本公司具有ISO9001认证。经现场复核确认原件暂时不能出示。",
            "本公司具有ISO9001认证。经现场复核确认暂时不能出示原件。",
            "本公司具有ISO9001认证。经核对暂不能提供扫描件。",
            "本公司具有ISO9001认证。经核对原件暂不能出示。",
            "本公司具有ISO9001认证。经核对未能出具证明材料。",
            "本公司具有ISO9001认证。经核对复印件无法补充。",
            "本公司具有ISO9001认证。扫描件未提供。",
            "本公司具有ISO9001认证。扫描件缺失。",
            "本公司具有ISO9001认证。经核对扫描件缺失。",
            "本公司具有ISO9001认证。复印件缺少。",
            "本公司具有ISO9001认证。经核对缺失证明材料。",
            "本公司具有ISO9001认证。复印件未提交。",
            "本公司具有ISO9001认证。该证书未提供。",
            "本公司具有ISO9001认证。该证书扫描件无法提供。",
            "本公司具有ISO9001认证。复印件不能提供。",
            "本公司具有ISO9001认证。经核对复印件未附。",
            "本公司具有ISO9001认证。证明材料未上传。",
            "本公司具有ISO9001认证。复印件待补。",
            "本公司具有ISO9001认证。扫描件未附。"})
    void missingCertificateMaterialDefectPreservesManualReview(String text) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        var bid = DocumentContent.fromText("bid", "hash", text);
        assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证")).isFalse();
        assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                List.of(evidence)).orElseThrow().status()).as("入口: %s", text)
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
        assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).as("最终边界: %s", text)
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                req, java.util.Set.of()).stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                .findFirst().orElseThrow();
        assertThat(result.status()).as("引擎: %s", text).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "本授权委托书尚未签字、尚未盖章", "签名：无", "盖章：未加盖", "签字：空缺"})
    void originalAuthorizationDefectCannotBeHiddenByTranscription(String defect) {
        String text = "授权委托书\n法定代表人：张三\n受托人：李四\n授权范围：提交投标文件"
                + "\n授权期限：长期有效\n" + defect + "。\n【页面图像转写，非验签】图像可见公章";
        var bid = new DocumentContent("bid", "hash", text,
                List.of(new DocumentContent.PageContent(1, text, List.of())),
                List.of(new com.xiyu.bid.compliance.domain.PageImage(
                        0, "image/png", "授权委托书", 1, 0, 0, "base64")));
        var pass = QualityCheckItemResult.pass(BidQualityCheckItem.AUTHORIZATION_LETTER, "要素齐全", List.of());
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(AuthorizationFieldPolicy.hasSignedAuthorizationEvidence(bid)).isFalse();
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(),
                    TenderRequirements.empty(), java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.AUTHORIZATION_LETTER.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "第三章 合同条款。本合同到期后自动终止", "本合同到期后自动终止",
            "经核对合同已失效", "售后服务协议已到期", "报价有效期已届满",
            "第三章 合同条款。经查已终止", "第3节 商务条款。已到期", "\f合同已到期",
            "\f经核对：\n合同已到期", "\f经核对，\n报价表未提供",
            "\f经现场核实：\nISO14001认证已过期",
            "\f经核对：\n第三章 合同条款。证书未提供",
            "\f经核对\n合同已到期", "\f经核对\n报价表未提供",
            "\f经现场核实\nISO14001认证已过期",
            "\f经核对\n第三章 合同条款。证书未提供",
            "\f经复核确认后：\n合同已到期", "\f经现场核实后：报价表未提供",
            "\f核对结果：ISO14001认证已过期",
            "\f经复核确认后：\n第三章 合同条款。证书未提供"})
    void unrelatedBusinessStatusCannotInheritCertificateContext(String continuation) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String firstPage = "本公司具有ISO9001认证。";
        String text = firstPage + continuation + "。";
        boolean separatePage = continuation.startsWith("\f");
        var bid = new DocumentContent("bid", "hash", text,
                separatePage ? List.of(new DocumentContent.PageContent(1, firstPage, List.of()),
                        new DocumentContent.PageContent(2, continuation.substring(1), List.of()))
                        : List.of(new DocumentContent.PageContent(1, text, List.of())), List.of());
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证")).isTrue();
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口").isEqualTo(QualityCheckStatus.PASS);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界").isEqualTo(QualityCheckStatus.PASS);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎").isEqualTo(QualityCheckStatus.PASS);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "\f经核对：\f证书未提供", "\f经核对\f证书未提供", "\f核对结果：\f证书已过期"})
    void intermediateExplanationPageWithDefectCannotPass(String continuation) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String firstPage = "本公司具有ISO9001认证。";
        String text = firstPage + continuation + "。";
        var bid = DocumentContent.fromText("bid", "hash", text);
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证")).isFalse();
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口").isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界").isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎").isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "该证书尚未到期", "该证书未到期", "该证书尚未过期", "该证书尚未失效",
            "该证书未过期", "该证书未失效"})
    void negatedInvalidStatusPreservesValidCertificate(String continuation) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String firstPage = "本公司具有ISO9001认证。";
        String text = firstPage + continuation + "。";
        var bid = DocumentContent.fromText("bid", "hash", text);
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证")).isTrue();
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口").isEqualTo(QualityCheckStatus.PASS);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界").isEqualTo(QualityCheckStatus.PASS);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎").isEqualTo(QualityCheckStatus.PASS);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "该证书已过期", "该证书已失效", "该证书经查已吊销", "该证书复印件未提供",
            "本证书已到期", "此证书已过期", "上述证书不再有效",
            "证书已过期", "证书已失效", "证书复印件未提供", "证书借用于合作方",
            "认证已过期", "扫描件未提供",
            "有效期限：2025-12-31", "截止日期：2025-12-31", "到期日期：2025-12-31",
            "经核对证书已过期", "经现场核实证书未提供", "经核对扫描件未提供", "经查证书已吊销", "经排查证书复印件缺失",
            "经现场核实：证书已过期", "经现场核实：证书未提供", "经核对：扫描件未提供", "经核查: 证书已过期",
            "经现场核实，证书已过期", "经现场核实，证书未提供", "经核对，扫描件未提供", "经查，证书已吊销",
            "但是：证书已作废", "但是，证书未提供",
            "经核对：\n证书已过期", "经核对：\n证书未提供",
            "经现场核实：\n证书已过期", "经现场核实：\n证书未提供",
            "经核对，\n扫描件未提供", "经核查: \r\n证书已过期",
            "经查：\n\n证书已吊销", "经核对：\n  证书未提供",
            "但是：\n证书已作废", "但是，\n证书未提供",
            "经核对\n证书已过期", "经核对\n证书未提供",
            "经现场核实\r\n扫描件未提供", "经查\n\n证书已吊销",
            "经核对  \n  证书未提供", "但是\n证书已作废",
            "经核对本公司没有取得认证", "经核对：本公司没有取得认证",
            "本公司经现场核实未持有证书", "本公司没有取得认证", "本公司没有证书",
            "经复核确认后：\n证书已过期", "经现场核实后：证书未提供", "核对结果：证书已过期",
            "经复核确认后证书已过期", "经复核确认后\n证书未提供", "核对结果：\n证书未提供",
            "经审核确认后：\r\n扫描件缺失", "复核结论：证书已失效",
            "复核后证书已过期", "查验资料后发现证书已失效", "再次确认后扫描件未提供",
            "复核后：\n证书已过期", "补充说明如下。证书已过期",
            "经核对，已过期", "经核对：已过期", "经现场核实，已失效", "但是，已作废", "经核对已过期"})
    void crossPageExplicitCertificateDefectCannotPass(String defect) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String firstPage = "本公司具有ISO9001认证。";
        String secondPage = defect + "。";
        String text = firstPage + "\n\f\n" + secondPage;
        var bid = new DocumentContent("bid", "hash", text,
                List.of(new DocumentContent.PageContent(1, firstPage, List.of()),
                        new DocumentContent.PageContent(2, secondPage, List.of())), List.of());
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证")).as(defect).isFalse();
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口: %s", defect)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界: %s", defect).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎: %s", defect).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "ISO9001认证证明材料", "ISO9001认证扫描件", "ISO9001认证复印件"})
    void certificateMaterialHeadingPreservesPositiveStatement(String heading) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        var bid = DocumentContent.fromText("bid", "hash", heading + "\n本公司具有ISO9001认证。");
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(bid.fullText(), "ISO9001认证")).isTrue();
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(heading, "ISO9001认证")).isFalse();
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口").isEqualTo(QualityCheckStatus.PASS);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).as("最终边界").isEqualTo(QualityCheckStatus.PASS);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode())).findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎").isEqualTo(QualityCheckStatus.PASS);
        });
    }

    @org.junit.jupiter.api.Test
    void certificateWithSlashCodeMatchesPositiveStatement() {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "GB/T19001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有GB/T19001认证"),
                List.of(), List.of(), List.of(), false);
        String text = "本公司具有GB/T19001认证。";
        var bid = DocumentContent.fromText("bid", "hash", text);
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "GB/T19001认证")).isTrue();
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口").isEqualTo(QualityCheckStatus.PASS);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).as("最终边界").isEqualTo(QualityCheckStatus.PASS);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode())).findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎").isEqualTo(QualityCheckStatus.PASS);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "该证书有效期已经结束", "该证书有效期已结束", "该证书已截止", "该证书有效期已满",
            "该证书超出有效期", "该证书失去效力", "该证书废止", "该证书已撤销", "该证书已终止"})
    void certificateValidityEndedCannotPass(String defect) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String text = "本公司具有ISO9001认证。" + defect + "。";
        var bid = DocumentContent.fromText("bid", "hash", text);
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证")).as(defect).isFalse();
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口: %s", defect)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界: %s", defect).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎: %s", defect).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "经现场核实证书未提供", "经现场核实未提供证书", "证书无法提供", "证书未提交", "未随标书提供证书"})
    void certificateMaterialMissingCannotPass(String continuation) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String text = "本公司具有ISO9001认证。" + continuation + "。";
        var bid = DocumentContent.fromText("bid", "hash", text);
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证")).as(continuation).isFalse();
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界: %s", continuation).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎: %s", continuation).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "该证书未随本次投标文件一并提交, MANUAL_REVIEW",
            "该证书尚未随标书一同提交, MANUAL_REVIEW",
            "该证书不随本次投标文件同时报送, MANUAL_REVIEW",
            "该证书拒绝随投标文件一并提供, MANUAL_REVIEW",
            "经核对未随本次投标文件一并提交证书, MANUAL_REVIEW",
            "该证书未随本次投标文件 一并 提交, MANUAL_REVIEW",
            "该证书已随本次投标文件一并提交, PASS",
            "该证书已随标书一同提供, PASS",
            "该证书已随本次投标文件同时报送, PASS"})
    void accompanyingSubmissionStatusControlsEntryBoundaryAndEngine(String continuation, QualityCheckStatus expected) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String text = "本公司具有ISO9001认证。" + continuation + "。";
        var bid = DocumentContent.fromText("bid", "hash", text);
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证"))
                    .as("纯函数: %s", continuation).isEqualTo(expected == QualityCheckStatus.PASS);
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口: %s", continuation).isEqualTo(expected);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界: %s", continuation).isEqualTo(expected);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎: %s", continuation).isEqualTo(expected);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "项目经理未到场, PASS", "售后服务未包含安装, PASS",
            "人员配置未完成, PASS", "安装服务尚未提供, PASS",
            "项目经理未到场。已过期, PASS",
            "售后服务未包含安装。该证书已过期, MANUAL_REVIEW",
            "该证书尚未确认, MANUAL_REVIEW", "未取得, MANUAL_REVIEW"})
    void unrelatedBusinessDefectsDoNotChangeCertificateStatus(String continuation, QualityCheckStatus expected) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String text = "本公司具有ISO9001认证。" + continuation + "。";
        var bid = DocumentContent.fromText("bid", "hash", text);
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证"))
                    .as("纯函数: %s", continuation).isEqualTo(expected == QualityCheckStatus.PASS);
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口: %s", continuation).isEqualTo(expected);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界: %s", continuation).isEqualTo(expected);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎: %s", continuation).isEqualTo(expected);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "经现场复核确认：未在本次标书中提供证书扫描件",
            "经现场复核确认：未在本次投标文件中提交证书复印件",
            "经现场复核确认：尚未在标书中一并提供证书扫描件",
            "经现场复核确认：不能在本次标书中提供证书原件",
            "经现场复核确认：未在本次标书内上传证书扫描件",
            "经核对合同附件中的证书未提供", "合同附件中的证书未提供",
            "经核对协议附件中的认证未提交", "经核对报价附件中的证书缺失",
            "经核对合同所附证书未提供", "经核对合同附件中的扫描件未提供"})
    void certificateMaterialLocationCannotClearCertificateContext(String continuation) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String text = "本公司具有ISO9001认证。" + continuation + "。";
        var bid = DocumentContent.fromText("bid", "hash", text);
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证"))
                    .as("持证事实: %s", continuation).isFalse();
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口: %s", continuation)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界: %s", continuation).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎: %s", continuation)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "该证书不再由本公司持有", "该证书不再由我司持有", "该证书不再由我方持有", "该证书不再由本投标人持有",
            "该证书不归本公司所有", "该证书不归我司所有", "该证书不归我方所有", "该证书不再归本公司所有", "该证书未归本公司所有",
            "该证书未予提交", "该证书未予提供", "该证书未予出示", "该证书未予上传", "该证书不予提交", "该证书不予提供"})
    void negatedOwnershipAndUnsubmittedCertificateCannotPass(String continuation) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String text = "本公司具有ISO9001认证。" + continuation + "。";
        var bid = DocumentContent.fromText("bid", "hash", text);
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证"))
                    .as("持证事实: %s", continuation).isFalse();
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口: %s", continuation)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界: %s", continuation).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎: %s", continuation)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "经核对本公司没有取得认证", "经核对我司没有持有认证",
            "经核对我方没有获得认证", "经核对本投标人没有通过认证",
            "经核对本公司没有具备认证", "经核对本公司没有具有认证"})
    void noCertificateAdmissionCannotBeOverriddenByKnowledgeEvidence(String continuation) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String text = "本公司具有ISO9001认证。" + continuation + "。";
        var bid = DocumentContent.fromText("bid", "hash", text);
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证"))
                    .as("持证事实: %s", continuation).isFalse();
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口: %s", continuation)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界: %s", continuation).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎: %s", continuation)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "证书已过期", "已过期", "已被吊销", "现已被撤销", "实际已过期",
            "经核对，已过期", "经现场核实，已失效", "但是，已作废"})
    void blankIntermediatePagePreservesCertificateDefectAssociation(String defect) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String firstPage = "本公司具有ISO9001认证。";
        String blankPage = "";
        String thirdPage = defect + "。";
        String text = firstPage + "\n\f\n" + blankPage + "\n\f\n" + thirdPage;
        var bid = new DocumentContent("bid", "hash", text,
                List.of(new DocumentContent.PageContent(1, firstPage, List.of()),
                        new DocumentContent.PageContent(2, blankPage, List.of()),
                        new DocumentContent.PageContent(3, thirdPage, List.of())), List.of());
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证"))
                    .as("纯函数: %s", defect).isFalse();
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口: %s", defect)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界: %s", defect).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎: %s", defect).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"说明如下。", "第2页\n西域投标文件\n共3页", "说明如下。\n\f\n第3页"})
    void neutralIntermediatePagesPreserveCertificateDefectAssociation(String middle) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String firstPage = "本公司具有ISO9001认证。";
        String thirdPage = "已过期。";
        for (boolean paginated : List.of(false, true)) {
            String separator = paginated ? "\n\f\n" : "\n";
            String text = firstPage + separator + middle + separator + thirdPage;
            var bid = new DocumentContent("bid", "hash", text,
                    paginated ? List.of(new DocumentContent.PageContent(1, firstPage, List.of()),
                            new DocumentContent.PageContent(2, middle, List.of()),
                            new DocumentContent.PageContent(3, thirdPage, List.of()))
                            : List.of(new DocumentContent.PageContent(1, text, List.of())), List.of());
            org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证"))
                        .as("纯函数: %s", middle).isFalse();
                softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                        List.of(evidence)).orElseThrow().status()).as("入口: %s", middle)
                        .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
                var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
                softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                        .as("最终边界: %s", middle).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
                var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                        req, java.util.Set.of()).stream()
                        .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                        .findFirst().orElseThrow();
                softly.assertThat(result.status()).as("引擎: %s", middle).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            });
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"资料随合同一并装订。", "材料随协议一并归档。", "资料与报价表一起装订。", "附件包含售后承诺。"})
    void incidentalBusinessMentionsPreserveCertificateDefectAssociation(String middle) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String firstPage = "本公司具有ISO9001认证。";
        String thirdPage = "该证书已过期。";
        for (boolean paginated : List.of(false, true)) {
            String separator = paginated ? "\n\f\n" : "";
            String text = firstPage + separator + middle + separator + thirdPage;
            var bid = new DocumentContent("bid", "hash", text,
                    paginated ? List.of(new DocumentContent.PageContent(1, firstPage, List.of()),
                            new DocumentContent.PageContent(2, middle, List.of()),
                            new DocumentContent.PageContent(3, thirdPage, List.of()))
                            : List.of(new DocumentContent.PageContent(1, text, List.of())), List.of());
            org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证"))
                        .as("纯函数: %s", middle).isFalse();
                softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                        List.of(evidence)).orElseThrow().status()).as("入口: %s", middle)
                        .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
                var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
                softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                        .as("最终边界: %s", middle).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
                var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                        req, java.util.Set.of()).stream()
                        .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                        .findFirst().orElseThrow();
                softly.assertThat(result.status()).as("引擎: %s", middle).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            });
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"说明如下。\n合同履行说明。", "第2页\n本公司具有ISO14001认证。", "说明如下。\n第二章 商务响应"})
    void explicitObjectSwitchAcrossIntermediatePagesClearsCertificateAssociation(String middle) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String firstPage = "本公司具有ISO9001认证。";
        String thirdPage = "已过期。";
        for (boolean paginated : List.of(false, true)) {
            String separator = paginated ? "\n\f\n" : "\n";
            String text = firstPage + separator + middle + separator + thirdPage;
            var bid = new DocumentContent("bid", "hash", text,
                    paginated ? List.of(new DocumentContent.PageContent(1, firstPage, List.of()),
                            new DocumentContent.PageContent(2, middle, List.of()),
                            new DocumentContent.PageContent(3, thirdPage, List.of()))
                            : List.of(new DocumentContent.PageContent(1, text, List.of())), List.of());
            org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证"))
                        .as("纯函数: %s", middle).isTrue();
                softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                        List.of(evidence)).orElseThrow().status()).as("入口: %s", middle)
                        .isEqualTo(QualityCheckStatus.PASS);
                var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
                softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                        .as("最终边界: %s", middle).isEqualTo(QualityCheckStatus.PASS);
                var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                        req, java.util.Set.of()).stream()
                        .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                        .findFirst().orElseThrow();
                softly.assertThat(result.status()).as("引擎: %s", middle).isEqualTo(QualityCheckStatus.PASS);
            });
        }
    }

    @Test
    void blankIntermediatePageDoesNotInheritUnrelatedContext() {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String firstPage = "本公司具有ISO9001认证。";
        String blankPage = "   \n\t  ";
        String thirdPage = "合同已到期。";
        String text = firstPage + "\n\f\n" + blankPage + "\n\f\n" + thirdPage;
        var bid = new DocumentContent("bid", "hash", text,
                List.of(new DocumentContent.PageContent(1, firstPage, List.of()),
                        new DocumentContent.PageContent(2, blankPage, List.of()),
                        new DocumentContent.PageContent(3, thirdPage, List.of())), List.of());
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证")).isTrue();
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).isEqualTo(QualityCheckStatus.PASS);
        });
    }


    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "已被吊销。\n本公司具有ISO9001认证。",
            "已被吊销。\n\f\n本公司具有ISO9001认证。",
            "现已被撤销。\n\f\n本公司具有ISO9001认证。",
            "已被暂停。\n本公司具有ISO9001认证。",
            "已被暂停。\n\f\n本公司具有ISO9001认证。",
            "核验说明。该证书已过期。本公司具有ISO9001认证。",
            "核验说明。该证书已过期。说明如下。本公司具有ISO9001认证。",
            "核验说明。该证书已过期。\n\f\n第2页\n说明如下。\n本公司具有ISO9001认证。",
            "核验说明。\n\f\n该证书复印件未提供。\n\f\n本公司具有ISO9001认证。"})
    void precedingCertificateDefectSurvivesPrefaceExplanationAndPagination(String text) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        var pages = new java.util.ArrayList<DocumentContent.PageContent>();
        for (String page : text.split("\f")) {
            pages.add(new DocumentContent.PageContent(pages.size() + 1, page, List.of()));
        }
        var bid = new DocumentContent("bid", "hash", text, pages, List.of());
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证"))
                    .as("纯函数: %s", text).isFalse();
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口: %s", text)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界: %s", text).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎: %s", text).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "本公司具有ISO9001认证。安全生产许可证。该证书已过期。",
            "本公司具有ISO9001认证。\n\f\n安全生产许可证。该证书已过期。",
            "本公司具有ISO9001认证。\n\f\n安全生产许可证。\n\f\n该证书已过期。",
            "本公司具有ISO9001认证。安全生产许可证。已被暂停。",
            "本公司具有ISO9001认证。\n\f\n安全生产许可证。\n\f\n已被暂停。",
            "本公司具有ISO9001认证。合同履行说明。已被暂停。",
            "本公司具有ISO9001认证。\n\f\n合同履行说明。\n\f\n已被暂停。"})
    void namedCertificateSwitchIsIndependentOfPagination(String text) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        var pages = new java.util.ArrayList<DocumentContent.PageContent>();
        for (String page : text.split("\f")) {
            pages.add(new DocumentContent.PageContent(pages.size() + 1, page, List.of()));
        }
        var bid = new DocumentContent("bid", "hash", text, pages, List.of());
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证"))
                    .as("纯函数: %s", text).isTrue();
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口: %s", text)
                    .isEqualTo(QualityCheckStatus.PASS);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界: %s", text).isEqualTo(QualityCheckStatus.PASS);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎: %s", text).isEqualTo(QualityCheckStatus.PASS);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "本公司具有ISO9001认证。安全生产许可证。该证书已过期。",
            "本公司具有ISO9001认证。\n\f\n安全生产许可证。该证书已过期。",
            "本公司具有ISO9001认证。\n\f\n安全生产许可证。\n\f\n该证书已过期。",
            "核验说明。该证书已过期。合同履行说明。本公司具有ISO9001认证。",
            "核验说明。该证书已过期。本公司具有ISO14001认证。本公司具有ISO9001认证。",
            "本公司具有ISO14001认证。该证书已过期。本公司具有ISO9001认证。",
            "核验说明。该证书未过期。说明如下。本公司具有ISO9001认证。"})
    void precedingDefectRetainsIdentityBoundariesAndNegatedStatus(String text) {
        assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证"))
                .as(text).isTrue();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "投标有效期：90日。该证书已过期",
            "交货期：30天。该证书已失效",
            "付款方式：电汇。此证书已吊销",
            "质保期：三年。上述证书已过期",
            "第三章 商务条款。该资质已注销"})
    void explicitCertificateDefectAcrossUnrelatedContextBoundaryCannotPass(String continuation) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String text = "本公司具有ISO9001认证。" + continuation + "。";
        var bid = DocumentContent.fromText("bid", "hash", text);
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证")).as(continuation).isFalse();
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口: %s", continuation)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界: %s", continuation).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎: %s", continuation).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        });
    }

    @org.junit.jupiter.api.Test
    void ordinaryInstructionSentenceCannotMaskSubsequentCertificateExpiration() {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        String text = "本公司具有ISO9001认证。\n附件包含填写说明。\n该证书已过期。";
        var bid = DocumentContent.fromText("bid", "hash", text);
        assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证")).isFalse();
        assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                List.of(evidence)).orElseThrow().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "本公司具有ISO9001认证。有效性待确认。",
            "本公司具有ISO9001认证。持证状态待确认。",
            "本公司具有ISO9001认证。待确认。",
            "本公司具有ISO9001认证。待核实。",
            "本公司具有ISO9001认证。目前暂停使用。",
            "本公司具有ISO9001认证。暂停使用。",
            "本公司具有ISO9001认证。现已暂停使用。",
            "本公司具有ISO9001认证。目前状态存疑。",
            "本公司具有ISO9001认证。停止使用。"})
    void unconfirmedCertificateContinuationTurnsToManualReview(String text) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        var bid = DocumentContent.fromText("bid", "hash", text);
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证"))
                    .as("纯函数: %s", text).isFalse();
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口: %s", text)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界: %s", text).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎: %s", text).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "有效性待确认。", "持证状态待确认。", "目前暂停使用。"})
    void unconfirmedCertificateContinuationAcrossPagesTurnsToManualReview(String continuation) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        var pages = List.of(
                new DocumentContent.PageContent(1, "本公司具有ISO9001认证。", List.of()),
                new DocumentContent.PageContent(2, continuation, List.of()));
        var bid = new DocumentContent("bid", "hash", "本公司具有ISO9001认证。\n\f\n" + continuation, pages, List.of());
        org.assertj.core.api.SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(bid.fullText(), "ISO9001认证"))
                    .as("纯函数: %s", continuation).isFalse();
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口: %s", continuation)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界: %s", continuation).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎: %s", continuation).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"第二章 投标人资格响应", "第2节 资格响应", "## 第二章 投标人资格响应", "第二部分 资格响应"})
    void ordinarySectionAfterExampleCannotHideCertificateContradiction(String heading) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        for (String separator : List.of("\n", "\n\f\n")) {
            String text = "本公司具有ISO9001认证。\n填写示例：\n示例内容" + separator
                    + heading + "\n本公司未取得ISO9001认证。";
            var bid = DocumentContent.fromText("bid", "hash", text);
            assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证")).isFalse();
            assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            assertThat(BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match), req,
                    java.util.Set.of()).stream().filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"第二章 技术响应", "第2节 技术响应", "## 第二章 技术响应", "第二部分 技术响应"})
    void ordinarySectionAfterExampleRetainsLocatedScoringBlank(String heading) {
        String quote = "技术方案：____";
        var fail = QualityCheckItemResult.fail(BidQualityCheckItem.SCORING_RESPONSE,
                "技术方案未填写", "请补齐", List.of(QualityEvidence.bid("缺陷", 2, quote)));
        for (boolean headingOnPreviousPage : List.of(false, true)) {
            String firstPage = "填写示例：\n示例内容" + (headingOnPreviousPage ? "\n" + heading : "");
            String secondPage = (headingOnPreviousPage ? "" : heading + "\n") + quote;
            var bid = new DocumentContent("bid", "hash", firstPage + "\n\f\n" + secondPage,
                    List.of(new DocumentContent.PageContent(1, firstPage, List.of()),
                            new DocumentContent.PageContent(2, secondPage, List.of())), List.of());
            assertThat(SemanticEvidenceBoundary.reconcile(fail, bid).status()).isEqualTo(QualityCheckStatus.FAIL);
        }
    }

}
