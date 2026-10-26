package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationPositiveContractTest {
    private static DocumentContent doc(String scope, String term) {
        return DocumentContent.fromText("bid.pdf", "hash", "授权委托书\n法定代表人：张三\n委托代理人：李四\n授权范围：" + scope + "\n授权期限：" + term);
    }
    @Test void placeholderAndUnknownTermsNeverPassEvenWithNamedPeopleAndActions() {
        for (String term : List.of("【此处填写】", "[请输入]", "____", "", "另行商议", "无限期", "以口头约定为准", "待核实")) {
            assertThat(AuthorizationLetterCheckPolicy.check(doc("签署投标文件", term)).status())
                    .as("term=%s", term).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }
    @Test void unknownOrNegatedScopeCannotPassWithValidTerm() {
        for (String scope : List.of("【补充内容】", "", "见其他材料", "不得签署投标文件", "全权代表", "自行决定")) {
            assertThat(AuthorizationLetterCheckPolicy.check(doc(scope, "2026年9月21日至2026年12月31日")).status())
                    .as("scope=%s", scope).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }
    @Test void impossibleOrReversedDatesAreNotFilledTerms() {
        for (String term : List.of("2026年2月30日至2026年12月31日", "2026年12月31日至2026年9月21日", "2026年13月1日至2027年1月1日")) {
            assertThat(AuthorizationLetterCheckPolicy.check(doc("签署投标文件", term)).status())
                    .as("term=%s", term).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }
    @Test void explicitActionsAndOrderedRealDatesCanPass() {
        assertThat(AuthorizationLetterCheckPolicy.check(doc("签署投标文件", "2026年9月21日至2026年12月31日")).status())
                .isEqualTo(QualityCheckStatus.PASS);
    }
    @Test void aiPassCannotOverrideUnprovenAuthorizationFields() {
        var llm = new LlmAdjudicator(prompt -> LlmVerdict.success(new LlmVerdict("PASS", "", "AI认为完整", "")));
        var results = BidDocumentQualityEngine.evaluateAll(doc("签署投标文件", "【此处填写】"),
                DocumentContent.empty(), List.of(), TenderRequirements.empty(), Set.of(), llm, (p, s) -> { });
        var auth = results.stream().filter(r -> r.itemCode().equals("AUTHORIZATION_LETTER")).findFirst().orElseThrow();
        assertThat(auth.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test void legalRepresentativeExemptionNeedsPopulatedIdentityProofNotJustTitle() {
        var requirement = new AuthorizationRequirement(true, true, "法定代表人身份证明", "允许法人参加");
        assertThat(AuthorizationLetterCheckPolicy.check(DocumentContent.fromText("bid.pdf", "hash", "法定代表人身份证明"), requirement).status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }
    @Test void conflictingActualAuthorizationsCannotBeSilentlyChosen() {
        var first = doc("签署投标文件", "2026年9月21日至2026年12月31日").fullText();
        var second = first.replace("李四", "王五");
        var document = new DocumentContent("bid.pdf", "hash", first + second, List.of(
                new DocumentContent.PageContent(11, first, List.of()), new DocumentContent.PageContent(12, second, List.of())));
        assertThat(AuthorizationLetterCheckPolicy.check(document).status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test void aiFailCannotTurnAnUnfilledFieldIntoCertainMaterialFailure() {
        var llm = new LlmAdjudicator(prompt -> LlmVerdict.success(new LlmVerdict("FAIL", "", "授权期限缺失", "补充授权期限")));
        var results = BidDocumentQualityEngine.evaluateAll(doc("签署投标文件", "____"),
                DocumentContent.empty(), List.of(), TenderRequirements.empty(), Set.of(), llm, (p, s) -> { });
        assertThat(results.stream().filter(r -> r.itemCode().equals("AUTHORIZATION_LETTER")).findFirst().orElseThrow().status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test void statusWordsAreNotRecognizedPeople() {
        for (String value : List.of("未知", "不详", "未提供", "待确认", "负责人", "姓名未定", "张某", "王某某")) {
            var document = DocumentContent.fromText("bid.pdf", "hash", "授权委托书\n法定代表人：" + value
                    + "\n委托代理人：" + value + "\n授权范围：提交投标文件\n授权期限：自签署日起至投标有效期结束");
            assertThat(AuthorizationLetterCheckPolicy.check(document).status()).as(value).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }
    @Test void incompleteSecondPageCannotHideItsConflictingPrincipal() {
        var first = doc("签署投标文件", "2026年9月21日至2026年12月31日").fullText();
        var second = first.replace("张三", "王五").replace("李四", "____");
        var document = new DocumentContent("bid.pdf", "hash", first + second, List.of(
                new DocumentContent.PageContent(11, first, List.of()), new DocumentContent.PageContent(12, second, List.of())));
        assertThat(AuthorizationLetterCheckPolicy.check(document).status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test void unresolvedActualBodyStillOutranksEarlierCompositionNoticeForEvidence() {
        var document = new DocumentContent("bid.pdf", "hash", "正文", List.of(
                new DocumentContent.PageContent(8, "投标文件包括授权委托书及身份证明", List.of()),
                new DocumentContent.PageContent(11, doc("签署投标文件", "【此处填写】").fullText(), List.of())));
        var result = AuthorizationLetterCheckPolicy.check(document);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.evidences().getFirst().pageNumber()).isEqualTo(11);
    }
}
