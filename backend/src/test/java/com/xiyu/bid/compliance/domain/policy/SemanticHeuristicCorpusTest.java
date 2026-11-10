// Input: 明确业务契约或已标记的历史启发式观察输入
// Output: 保留既有输入，断言收口后的明确预期；语料不作为默认门禁
// Pos: compliance/domain/policy - semantic boundary 技术债分层测试
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import com.xiyu.bid.compliance.domain.QualityEvidence;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@org.junit.jupiter.api.Tag("heuristic-corpus")
class SemanticHeuristicCorpusTest {

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"项目经理业绩证明", "项目管理机构配置"})
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
    @org.junit.jupiter.params.provider.ValueSource(strings = {"项目管理机构配置"})
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
    @org.junit.jupiter.params.provider.ValueSource(strings = {"本公司具有ISO9001认证。该证书已过期。", "已被吊销。"})
    void certificateSampleDefectsCannotContaminateFormalStatement(String sample) {
        for (String separator : List.of("\n", "\n\f\n")) {
            String text = "填写示例：\n" + sample + separator + "正式声明：\n本公司具有ISO9001认证。";
            assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证")).isTrue();
            String priorStatement = "本公司具有ISO9001认证。\n填写示例：\n" + sample
                    + separator + "正式正文：\n该证书有效。";
            assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(priorStatement, "ISO9001认证"))
                    .as("样例结束后的代词续句仍需复核").isFalse();
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
    @org.junit.jupiter.params.provider.ValueSource(strings = {"实施方案：[见第30页]", "本方案（技术方案）已完整提供",
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
    @org.junit.jupiter.params.provider.ValueSource(strings = {"我司本次未提供技术方案及实施组织设计正文",
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
    @org.junit.jupiter.params.provider.ValueSource(strings = {"参考格式\n技术方案：____", "技术方案已完整响应"})
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
    @org.junit.jupiter.params.provider.ValueSource(strings = {"参考格式\n技术方案：____",
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"须提供手写签名及公章印迹", "请签字并盖章"})
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
    @org.junit.jupiter.params.provider.ValueSource(strings = {"签字：未提供", ""})
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
    @org.junit.jupiter.params.provider.ValueSource(strings = {"该证书系假证", "该证书真实性无法确认",
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
    @org.junit.jupiter.params.provider.ValueSource(strings = {"本公司具有ISO9001认证（有效期至2029年12月31日）但系借用",
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
            "该证书属于 本公司, MANUAL_REVIEW",
            "该证书属于： 本公司, MANUAL_REVIEW",
            "该证书持证单位为 我司, MANUAL_REVIEW",
            "该认证持有单位： 本投标人, MANUAL_REVIEW",
            "该证书持证主体为： 投标人, MANUAL_REVIEW",
            "该证书属于　我方, MANUAL_REVIEW"})
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
    void certificateFactsAndContinuationsUseExplicitExpectations() {
        var evidence = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true));
        for (CorpusExpectation sample : List.of(
                new CorpusExpectation("本公司具有ISO9001认证（证书编号：ABCD1234）。",
                        QualityCheckStatus.PASS),
                new CorpusExpectation("本公司具有ISO9001认证。该证书编号：ABCD1234。",
                        QualityCheckStatus.MANUAL_REVIEW),
                new CorpusExpectation("本公司具有ISO9001认证，售后承诺缺页。",
                        QualityCheckStatus.PASS),
                new CorpusExpectation("本公司持有ISO9001认证，证书未过期。",
                        QualityCheckStatus.MANUAL_REVIEW),
                new CorpusExpectation("本公司具有ISO9001认证。",
                        QualityCheckStatus.PASS),
                new CorpusExpectation("本公司具有ISO9001认证。未附售后服务承诺。",
                        QualityCheckStatus.PASS),
                new CorpusExpectation("本公司具有ISO9001认证。经核对报价表未附。",
                        QualityCheckStatus.PASS),
                new CorpusExpectation("本公司具有ISO9001认证。售后承诺未提供清晰的扫描件。",
                        QualityCheckStatus.PASS),
                new CorpusExpectation("本公司具有ISO9001认证。第二章 合同材料未提交加盖公章的复印件。",
                        QualityCheckStatus.PASS),
                new CorpusExpectation("本公司具有ISO9001认证。ISO14001认证未提供清晰的扫描件。",
                        QualityCheckStatus.PASS),
                new CorpusExpectation("本公司具有ISO9001认证。经核对报价表尚未提供。",
                        QualityCheckStatus.PASS),
                new CorpusExpectation("本公司具有ISO9001认证。经核对报价表缺失。",
                        QualityCheckStatus.PASS),
                new CorpusExpectation("本公司具有ISO9001认证。经现场复核确认报价表暂时无法提供。",
                        QualityCheckStatus.PASS),
                new CorpusExpectation("本公司具有ISO9001认证。售后服务承诺缺少。",
                        QualityCheckStatus.PASS),
                new CorpusExpectation("本公司具有ISO9001认证。经核对售后服务承诺未提交。",
                        QualityCheckStatus.PASS),
                new CorpusExpectation("本公司具有ISO9001认证。ISO14001认证。该证书属于供应商。",
                        QualityCheckStatus.MANUAL_REVIEW),
                new CorpusExpectation("本公司具有ISO9001认证。该证书归属本公司。",
                        QualityCheckStatus.MANUAL_REVIEW),
                new CorpusExpectation("本公司具有ISO9001认证。该证书持证单位为本公司。",
                        QualityCheckStatus.MANUAL_REVIEW),
                new CorpusExpectation("本公司具有ISO9001认证。该认证持有单位：我司。",
                        QualityCheckStatus.MANUAL_REVIEW),
                new CorpusExpectation("本公司具有ISO9001认证。该证书持证主体为本投标人。",
                        QualityCheckStatus.MANUAL_REVIEW),
                new CorpusExpectation("本公司具有ISO9001认证。该证书由认证机构颁发。",
                        QualityCheckStatus.MANUAL_REVIEW),
                new CorpusExpectation("本公司具有ISO9001认证。待补报价表。",
                        QualityCheckStatus.PASS),
                new CorpusExpectation("本公司具有ISO9001认证。合同。权属待议。",
                        QualityCheckStatus.MANUAL_REVIEW),
                new CorpusExpectation("本公司具有ISO9001认证。合同。仅为借用。",
                        QualityCheckStatus.MANUAL_REVIEW),
                new CorpusExpectation("本公司具有ISO9001认证。合同。实际借用自合作方。",
                        QualityCheckStatus.MANUAL_REVIEW),
                new CorpusExpectation("本公司具有ISO9001认证。ISO14001认证。实际借用自合作方。",
                        QualityCheckStatus.MANUAL_REVIEW),
                new CorpusExpectation("本公司具有ISO9001认证。\n填写示例：\n实际借用自合作方。",
                        QualityCheckStatus.PASS),
                new CorpusExpectation("本公司具有ISO9001认证。ISO14001认证。实际为借用。",
                        QualityCheckStatus.MANUAL_REVIEW),
                new CorpusExpectation("本公司具有ISO9001认证。\n填写示例：\n仅为借用。",
                        QualityCheckStatus.PASS),
                new CorpusExpectation("本公司具有ISO9001认证。报价。状态待确认。",
                        QualityCheckStatus.MANUAL_REVIEW),
                new CorpusExpectation("本公司具有ISO9001认证。\n填写示例：\n权属待议。状态待确认。",
                        QualityCheckStatus.PASS),
                new CorpusExpectation("本公司具有ISO9001认证。ISO14001认证。该证书已过期。",
                        QualityCheckStatus.MANUAL_REVIEW))) {
            String text = sample.text();
            QualityCheckStatus expected = sample.status();
            var bid = DocumentContent.fromText("bid", "hash", text);
            var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                    List.of(), List.of(), List.of(), false);
            assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as(text).isEqualTo(expected);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).as(text)
                    .isEqualTo(expected);
        }
    }

    @Test
    void unresolvedOtherCertificateDefectsRequireReview() {
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
                assertThat(result.status()).as(bid.fullText()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
                var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO,
                        "通过", List.of(evidence));
                assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).as(bid.fullText())
                        .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            }
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
            "该证书由 本公司 持有, MANUAL_REVIEW",
            "该证书由我司取得, MANUAL_REVIEW",
            "该证书由本投标人持有, MANUAL_REVIEW",
            "ISO14001认证。该证书由上海甲公司持有, MANUAL_REVIEW",
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
    void certificateHeadingDoesNotResolveFollowingDefect(String otherCertificate) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        for (String defect : List.of("权属待议", "状态待确认", "该证书已过期", "经审核确认后已过期", "复印件未附", "复印件待补", "经核对复印件未附", "经重新审核证明材料暂缺", "经核对复印件尚未提供", "经核对尚未提供复印件", "扫描件缺失", "经核对扫描件缺失", "复印件缺少", "经现场复核确认扫描件暂时无法提供", "经现场复核确认暂时无法提供扫描件")) {
            var bid = DocumentContent.fromText("bid", "hash",
                    "本公司具有ISO9001认证。" + otherCertificate + "。" + defect + "。");
            assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口: %s / %s", otherCertificate, defect)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).as("最终边界: %s / %s", otherCertificate, defect)
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            assertThat(result.status()).as("引擎: %s / %s", otherCertificate, defect).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "ISO14001认证。证书编号：CN12345", "安全生产许可证。证书编号：CN12345",
            "ISO14001认证", "安全生产许可证",
            "ISO14001认证。持证单位为供应商", "安全生产许可证。持证单位为供应商"})
    void unattributedOwnershipAfterAnotherCertificateRequiresReview(String otherCertificate) {
        var match = new KnowledgeMatch(KnowledgeMatch.SOURCE_QUALIFICATION, "record", "ISO9001认证", true);
        var evidence = QualityEvidence.fromKnowledgeMatch(match);
        var req = new TenderRequirements(List.of(), List.of("投标人须具有ISO9001认证"),
                List.of(), List.of(), List.of(), false);
        var bid = DocumentContent.fromText("bid", "hash",
                "本公司具有ISO9001认证。" + otherCertificate + "。持证人为供应商。");
        assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                List.of(evidence)).orElseThrow().status()).as("入口: %s", otherCertificate)
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
        assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status()).as("最终边界: %s", otherCertificate)
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                req, java.util.Set.of()).stream()
                .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                .findFirst().orElseThrow();
        assertThat(result.status()).as("引擎: %s", otherCertificate).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
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
    @org.junit.jupiter.params.provider.MethodSource("unrelatedBusinessStatusCases")
    void unrelatedBusinessStatusCannotInheritCertificateContext(String continuation, QualityCheckStatus expected) {
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
            softly.assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证")).isEqualTo(expected == QualityCheckStatus.PASS);
            softly.assertThat(QualificationVetoPolicy.checkFirstStage(bid, DocumentContent.empty(), req,
                    List.of(evidence)).orElseThrow().status()).as("入口").isEqualTo(expected);
            var pass = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_VETO, "通过", List.of(evidence));
            softly.assertThat(SemanticEvidenceBoundary.reconcile(pass, bid).status())
                    .as("最终边界").isEqualTo(expected);
            var result = BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(match),
                    req, java.util.Set.of()).stream()
                    .filter(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_VETO.getCode()))
                    .findFirst().orElseThrow();
            softly.assertThat(result.status()).as("引擎").isEqualTo(expected);
        });
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> unrelatedBusinessStatusCases() {
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("第三章 合同条款。本合同到期后自动终止",
                        QualityCheckStatus.PASS),
                org.junit.jupiter.params.provider.Arguments.of("本合同到期后自动终止",
                        QualityCheckStatus.PASS),
                org.junit.jupiter.params.provider.Arguments.of("经核对合同已失效",
                        QualityCheckStatus.PASS),
                org.junit.jupiter.params.provider.Arguments.of("售后服务协议已到期",
                        QualityCheckStatus.PASS),
                org.junit.jupiter.params.provider.Arguments.of("报价有效期已届满",
                        QualityCheckStatus.PASS),
                org.junit.jupiter.params.provider.Arguments.of("第三章 合同条款。经查已终止",
                        QualityCheckStatus.MANUAL_REVIEW),
                org.junit.jupiter.params.provider.Arguments.of("第3节 商务条款。已到期",
                        QualityCheckStatus.MANUAL_REVIEW),
                org.junit.jupiter.params.provider.Arguments.of("\f合同已到期",
                        QualityCheckStatus.PASS),
                org.junit.jupiter.params.provider.Arguments.of("\f经核对：\n合同已到期",
                        QualityCheckStatus.PASS),
                org.junit.jupiter.params.provider.Arguments.of("\f经核对，\n报价表未提供",
                        QualityCheckStatus.PASS),
                org.junit.jupiter.params.provider.Arguments.of("\f经现场核实：\nISO14001认证已过期",
                        QualityCheckStatus.PASS),
                org.junit.jupiter.params.provider.Arguments.of("\f经核对：\n第三章 合同条款。证书未提供",
                        QualityCheckStatus.MANUAL_REVIEW),
                org.junit.jupiter.params.provider.Arguments.of("\f经核对\n合同已到期",
                        QualityCheckStatus.PASS),
                org.junit.jupiter.params.provider.Arguments.of("\f经核对\n报价表未提供",
                        QualityCheckStatus.PASS),
                org.junit.jupiter.params.provider.Arguments.of("\f经现场核实\nISO14001认证已过期",
                        QualityCheckStatus.PASS),
                org.junit.jupiter.params.provider.Arguments.of("\f经核对\n第三章 合同条款。证书未提供",
                        QualityCheckStatus.MANUAL_REVIEW),
                org.junit.jupiter.params.provider.Arguments.of("\f经复核确认后：\n合同已到期",
                        QualityCheckStatus.PASS),
                org.junit.jupiter.params.provider.Arguments.of("\f经现场核实后：报价表未提供",
                        QualityCheckStatus.PASS),
                org.junit.jupiter.params.provider.Arguments.of("\f核对结果：ISO14001认证已过期",
                        QualityCheckStatus.PASS),
                org.junit.jupiter.params.provider.Arguments.of("\f经复核确认后：\n第三章 合同条款。证书未提供",
                        QualityCheckStatus.MANUAL_REVIEW));
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
    void unattributedValidStatusStillRequiresReview(String continuation) {
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
            "该证书已随本次投标文件一并提交, MANUAL_REVIEW",
            "该证书已随标书一同提供, MANUAL_REVIEW",
            "该证书已随本次投标文件同时报送, MANUAL_REVIEW"})
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
            "项目经理未到场。已过期, MANUAL_REVIEW",
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
    void unattributedDefectRequiresReviewAfterOtherObject(String middle) {
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
    void certificateHeadingDoesNotResolveStatusAcrossPagination(String text) {
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
            "核验说明。该证书已过期。合同履行说明。本公司具有ISO9001认证。",
            "核验说明。该证书已过期。本公司具有ISO14001认证。本公司具有ISO9001认证。",
            "本公司具有ISO14001认证。该证书已过期。本公司具有ISO9001认证。",
            "核验说明。该证书未过期。说明如下。本公司具有ISO9001认证。"})
    void unattributedDefectRemainsUnresolvedAcrossIdentityBoundaries(String text) {
        assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(text, "ISO9001认证"))
                .as(text).isFalse();
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

    @Test
    void sampleBoundaryDoesNotResolveUnattributedCertificateFact() {
        String sample = "本公司未取得ISO9001认证。";
        for (String separator : List.of("\n", "\n\f\n")) {
            String priorStatement = "本公司具有ISO9001认证。\n填写示例：\n" + sample
                    + separator + "正式正文：\n该证书有效。";
            assertThat(SemanticEvidenceBoundary.hasPositiveCertificateStatement(priorStatement, "ISO9001认证"))
                    .as("样例结束后的代词续句仍需复核").isFalse();
        }
    }

    private record CorpusExpectation(String text, QualityCheckStatus status) { }
}
