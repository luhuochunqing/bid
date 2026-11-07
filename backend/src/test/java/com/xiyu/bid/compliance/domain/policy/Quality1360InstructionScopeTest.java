package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

/** R3 accepted instruction/object/polarity contracts through the real engine. */
class Quality1360InstructionScopeTest {
    @ParameterizedTest
    @ValueSource(strings = {"、", "及", "和", "与", "以及", "，"})
    void everyScoringBlankRequiresConfirmation(String separator) {
        String criteria = "须填写服务协议" + separator + "响应时间";
        assertStatus(criteria, "服务协议：_____。\n响应时间：2小时。", QualityCheckStatus.MANUAL_REVIEW);
        assertStatus(criteria, "服务协议：已签署。\n响应时间：_____小时。", QualityCheckStatus.MANUAL_REVIEW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"无需提供服务协议并填写响应时间，以上两项均不作要求",
            "无需提供服务协议并填写响应时间", "无需填写服务协议及响应时间", "无须提供服务协议且填写响应时间",
            "不必填写服务协议、响应时间", "可填写服务协议及响应时间"})
    void negativeOrOptionalScopeCannotCreateMandatoryObjects(String criteria) {
        assertStatus(criteria, "服务协议：已签署。\n响应时间：_____小时。", QualityCheckStatus.MANUAL_REVIEW);
        assertStatus(criteria, "服务协议：_____。\n响应时间：2小时。", QualityCheckStatus.MANUAL_REVIEW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"无需提供服务协议，但须填写响应时间", "无需提供服务协议并须填写响应时间",
            "须提供服务协议并填写响应时间"})
    void crossActionAndContrastRequireConfirmation(String criteria) {
        assertStatus(criteria, "服务协议：已签署。\n响应时间：_____小时。", QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void independentMandatoryClauseStillRequiresConfirmation() {
        assertStatus("无需提供服务协议；必须填写响应时间", "响应时间：_____小时。", QualityCheckStatus.MANUAL_REVIEW);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "须提供服务协议模板、响应时间|服务协议|MANUAL_REVIEW",
            "须提供服务协议模板、响应时间|响应时间|MANUAL_REVIEW",
            "须提供服务协议（仅供参考）、响应时间|服务协议|MANUAL_REVIEW",
            "须提供服务协议（仅供参考）、响应时间|响应时间|MANUAL_REVIEW",
            "须提供服务协议（参考样例，无需填写字段）并须填写响应时间|服务协议|MANUAL_REVIEW",
            "须提供服务协议（参考样例，无需填写字段）并须填写响应时间|响应时间|MANUAL_REVIEW",
            "须提供服务协议；无需填写响应时间|服务协议|MANUAL_REVIEW",
            "须提供服务协议；无需填写响应时间|响应时间|MANUAL_REVIEW"
    })
    void referenceAndPolarityBelongToTheirOwnObjects(String criteria, String label, QualityCheckStatus expected) {
        assertStatus(criteria, label + "：_____。", expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"如适用，须提供服务协议", "须提供服务协议或响应时间", "服务协议可不填写",
            "服务协议填写范围另见附件，尚未明确", "须提供服务协议，但是否填写取决于后续订单"})
    void unresolvedApplicabilityAndChoiceCannotBecomeModelPassOrHardFailure(String criteria) {
        assertStatus(criteria, "服务协议：_____。", QualityCheckStatus.MANUAL_REVIEW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"须提供服务协议（无需填写字段）", "须填写服务协议、响应时间（以上均不作要求）",
            "须提供服务协议、响应时间，均为参考样例"})
    void explicitObjectOrGroupQualificationCannotBeDiscarded(String criteria) {
        assertStatus(criteria, "服务协议：_____。", QualityCheckStatus.MANUAL_REVIEW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"如适用：\n须提供服务协议", "如适用\n须提供服务协议"})
    void aFormattingLineBreakDoesNotResetApplicability(String criteria) {
        assertStatus(criteria, "服务协议：_____。", QualityCheckStatus.MANUAL_REVIEW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"须提供技术说明及服务协议", "须提供填报说明、服务协议"})
    void verbsInsideMaterialNamesAreNotNewInstructions(String criteria) {
        assertStatus(criteria, "服务协议：_____。", QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void scoringItemOrderDoesNotChangeExplicitObligation() {
        var titleOnly = new TenderRequirements.ScoringItem("服务协议", 5.0, "");
        var required = new TenderRequirements.ScoringItem("售后服务方案", 5.0, "须提供服务协议");
        for (var items : List.of(List.of(titleOnly, required), List.of(required, titleOnly))) {
            var req = new TenderRequirements(items, List.of(), List.of(), List.of(), List.of(), false);
            var bid = DocumentContent.fromText("bid.docx", "bh", "服务协议：_____。");
            assertThat(ScoringResponsePolicy.checkFirstStage(bid, req).orElseThrow().status())
                    .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
        var onlyTitle = new TenderRequirements(List.of(titleOnly), List.of(), List.of(), List.of(), List.of(), false);
        assertThat(ScoringResponsePolicy.checkFirstStage(DocumentContent.fromText("b.docx", "bh", "服务协议：_____。"),
                onlyTitle).orElseThrow().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"须提供服务协议及响应时间", "无需提供服务协议并填写响应时间"})
    void unrelatedBlankDoesNotBecomeAnObligation(String criteria) {
        var bid = DocumentContent.fromText("bid.docx", "bh", "备注：_____。");
        assertThat(ScoringResponsePolicy.checkFirstStage(bid, requirements(criteria))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"须填写响应时间（如适用）", "须填写响应时间(如适用)",
            "须填写响应时间（若有相关订单）"})
    void conditionalObjectAnnotationRequiresReview(String criteria) {
        assertStatus(criteria, "响应时间：_____小时。", QualityCheckStatus.MANUAL_REVIEW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"技术方案模板", "技术方案参考样例", "技术方案"})
    void unboundTechnicalFieldsRequireReview(String field) {
        assertStatus("须填写响应时间", field + "：_____。", QualityCheckStatus.MANUAL_REVIEW);
        assertStatus("须填写技术方案", "技术方案：_____。", QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void unboundAnnotationScopeRequiresConfirmation() {
        assertStatus("须填写响应时间（如适用）、服务协议", "服务协议：_____。", QualityCheckStatus.MANUAL_REVIEW);
        assertStatus("须填写响应时间（如适用）、服务协议", "响应时间：_____小时。", QualityCheckStatus.MANUAL_REVIEW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"不是必须填写服务协议", "并不是必须填写服务协议", "不是无需填写服务协议",
            "尚未要求必须填写服务协议", "没有要求必须填写服务协议", "未明确要求必须填写服务协议",
            "尚未要求投标人必须填写服务协议", "没有规定所有参与本次投标的供应商应当填写服务协议",
            "并未要求本项目的报价人必须填写服务协议", "尚未明确要求投标人必须填写服务协议",
            "没有强制要求投标人必须填写服务协议", "不强制要求投标人必须填写服务协议",
            "尚未正式要求投标人必须填写服务协议"})
    void negatedModalityCannotProveMandatoryResponse(String criteria) {
        assertStatus(criteria, "服务协议：_____。", QualityCheckStatus.MANUAL_REVIEW);
        assertStatus("必须填写服务协议", "服务协议：_____。", QualityCheckStatus.MANUAL_REVIEW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"要求投标人必须填写服务协议", "规定参加投标的供应商应当填写服务协议",
            "尚未要求提供资质说明；规定投标人必须填写服务协议"})
    void positiveRequirementWithSubjectRetainsMandatoryScope(String criteria) {
        assertStatus(criteria, "服务协议：_____。", QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void contrastDoesNotCreateAnIndependentSourceProof() {
        assertStatus("未要求填写响应时间，但要求投标人必须填写服务协议", "服务协议：_____。", QualityCheckStatus.MANUAL_REVIEW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"不同标段均要求投标人必须填写服务协议", "不同标段要求投标人必须填写服务协议",
            "不正式要求投标人必须填写服务协议", "没有行政处罚的供应商要求投标人必须填写服务协议"})
    void unresolvedRequirementSourceDoesNotCertifyPositiveOrNegativeObligation(String criteria) {
        assertStatus(criteria, "服务协议：_____。", QualityCheckStatus.MANUAL_REVIEW);
        assertStatus("要求投标人必须填写服务协议", "服务协议：_____。", QualityCheckStatus.MANUAL_REVIEW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"历史示例要求投标人必须填写服务协议", "参考模板规定投标人必须填写服务协议",
            "往年文件要求投标人必须填写服务协议", "历史示例中投标人必须填写服务协议",
            "历史示例中，要求投标人必须填写服务协议", "（历史示例）要求投标人必须填写服务协议",
            "历史示例要求必须提供响应时间并必须填写服务协议",
            "须提供响应时间，参考模板中，必须填写服务协议"})
    void historicalOrReferenceSourceCannotProveCurrentObligation(String criteria) {
        assertStatus(criteria, "服务协议：_____。", QualityCheckStatus.MANUAL_REVIEW);
        assertStatus("本次招标文件要求投标人必须填写服务协议", "服务协议：_____。", QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void overlappingMaterialNamesDoNotProveTheSameRequiredField() {
        assertStatus("须填写产品延保服务协议", "服务协议：_____。", QualityCheckStatus.MANUAL_REVIEW);
        assertStatus("须填写服务协议", "服务协议：_____。", QualityCheckStatus.MANUAL_REVIEW);
        var qualified = new TenderRequirements(List.of(new TenderRequirements.ScoringItem("响应时间", 5.0,
                "必须填写售后响应时间，2小时以内得满分")), List.of(), List.of(), List.of(), List.of(), false);
        assertThat(ScoringResponsePolicy.checkFirstStage(
                DocumentContent.fromText("bid.docx", "bh", "技术方案\n响应时间：____小时"), qualified)
                .orElseThrow().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertStatus("必须填写响应时间", "响应时间：____小时", QualityCheckStatus.MANUAL_REVIEW);
    }

    private static void assertStatus(String criteria, String text, QualityCheckStatus expected) {
        var bid = new DocumentContent("bid.docx", "bh", text,
                List.of(new DocumentContent.PageContent(80, text, List.of())));
        var req = requirements(criteria);
        var first = ScoringResponsePolicy.checkFirstStage(bid, req).orElseThrow();
        assertThat(first.status()).as("first stage: %s / %s", criteria, text).isEqualTo(expected);
        for (String modelStatus : List.of("PASS", "FAIL")) {
            var llm = new LlmAdjudicator(p -> LlmVerdict.success(new LlmVerdict(modelStatus, "", "模型判断", "")));
            var result = BidDocumentQualityEngine.evaluateAll(bid,
                    DocumentContent.fromText("tender.pdf", "th", criteria), List.of(), req, Set.of(), llm,
                    (p, s) -> { }).stream().filter(r -> r.itemCode().equals("SCORING_RESPONSE")).findFirst().orElseThrow();
            assertThat(result.status()).as("engine/model %s: %s", modelStatus, criteria).isEqualTo(expected);
            assertThat(result.evidences()).isNotEmpty().allSatisfy(e -> {
                assertThat(e.pageNumber()).isEqualTo(80);
                assertThat(text).contains(e.quote());
            });
        }
    }

    private static TenderRequirements requirements(String criteria) {
        return new TenderRequirements(List.of(new TenderRequirements.ScoringItem("售后服务方案", 5.0, criteria)),
                List.of(), List.of(), List.of(), List.of(), false);
    }
}
